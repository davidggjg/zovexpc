#include "probes.h"
#include "log.h"

#include <fcntl.h>
#include <sys/wait.h>
#include <sys/mman.h>
#include <sys/stat.h>
#include <unistd.h>
#include <cerrno>
#include <cstring>
#include <string>

namespace zovex {

// Android 10+ blocks executable mappings of files in app-writable storage
// (W^X). Whether that block applies decides the whole runtime strategy:
//   allowed  -> Wine/Box64 binaries can be exec'd straight from the data dir
//   blocked  -> the runtime must go through the proot path instead
// We settle it by measurement at first launch, never by guessing from the API
// level, because OEM and SELinux policy vary.
namespace {

// Copies a known-good system binary into app storage and tries to execve it.
// This is the operation the runtime actually performs; an executable mapping
// succeeding says nothing about whether execve() is permitted.
bool tryExecve(const std::string& dir, std::string& detail) {
    const std::string target = dir + "/.zovex_execve_probe";

    int src = ::open("/system/bin/ls", O_RDONLY);
    if (src < 0) {
        detail = "Cannot open a system binary to copy for the probe.";
        return false;
    }
    int dst = ::open(target.c_str(), O_WRONLY | O_CREAT | O_TRUNC, 0700);
    if (dst < 0) {
        ::close(src);
        detail = std::string("Cannot write the probe binary: ") + std::strerror(errno);
        return false;
    }
    char buffer[1 << 16];
    ssize_t read_bytes;
    while ((read_bytes = ::read(src, buffer, sizeof(buffer))) > 0) {
        if (::write(dst, buffer, static_cast<size_t>(read_bytes)) != read_bytes) {
            ::close(src); ::close(dst); ::unlink(target.c_str());
            detail = "Short write while copying the probe binary.";
            return false;
        }
    }
    ::close(src);
    ::close(dst);
    ::chmod(target.c_str(), 0700);

    pid_t pid = ::fork();
    if (pid < 0) {
        ::unlink(target.c_str());
        detail = std::string("fork failed: ") + std::strerror(errno);
        return false;
    }

    if (pid == 0) {
        // Child: silence output, then attempt the exec under test.
        int devnull = ::open("/dev/null", O_WRONLY);
        if (devnull >= 0) { ::dup2(devnull, 1); ::dup2(devnull, 2); }
        char* const argv[] = { const_cast<char*>(target.c_str()), nullptr };
        char* const envp[] = { nullptr };
        ::execve(target.c_str(), argv, envp);
        // Report the exact errno back through the exit status.
        ::_exit(errno);
    }

    int status = 0;
    ::waitpid(pid, &status, 0);
    ::unlink(target.c_str());

    const int code = WIFEXITED(status) ? WEXITSTATUS(status) : -1;
    if (code == 0) {
        detail = "execve() from app storage is permitted.";
        return true;
    }
    detail = std::string("execve() refused (errno ") + std::to_string(code) + ": " +
             std::strerror(code) + "). The runtime cannot launch processes from app storage.";
    return false;
}

} // namespace

ExecInfo probeExecMapping(const std::string& dir) {
    ExecInfo info;
    info.path = dir + "/.zovex_exec_probe";

    int fd = ::open(info.path.c_str(), O_RDWR | O_CREAT | O_TRUNC, 0700);
    if (fd < 0) {
        info.detail = std::string("Cannot create probe file: ") + std::strerror(errno);
        return info;
    }

    // A single AArch64 `ret` instruction, so the page holds something real.
    const unsigned char ret[] = {0xc0, 0x03, 0x5f, 0xd6};
    if (::write(fd, ret, sizeof(ret)) != static_cast<ssize_t>(sizeof(ret))) {
        info.detail = std::string("Cannot write probe file: ") + std::strerror(errno);
        ::close(fd);
        ::unlink(info.path.c_str());
        return info;
    }

    const size_t pageSize = static_cast<size_t>(::sysconf(_SC_PAGESIZE));
    void* map = ::mmap(nullptr, pageSize, PROT_READ | PROT_EXEC, MAP_PRIVATE, fd, 0);
    if (map == MAP_FAILED) {
        info.detail = std::string("Executable mapping denied (") + std::strerror(errno) +
                      "). The runtime cannot run binaries from app storage.";
        ZLOGW("W^X probe: PROT_EXEC denied in %s: %s", dir.c_str(), std::strerror(errno));
        ::close(fd);
        ::unlink(info.path.c_str());
        return info;
    }

    ::munmap(map, pageSize);
    ::close(fd);
    ::unlink(info.path.c_str());

    info.mmapAllowed = true;
    ZLOGI("W^X probe: PROT_EXEC mapping allowed in %s", dir.c_str());

    // The mapping is not the thing that matters; execve() is.
    std::string execDetail;
    info.execveAllowed = tryExecve(dir, execDetail);
    info.ok = info.execveAllowed;
    info.detail = execDetail;
    ZLOGI("execve probe: %s", execDetail.c_str());
    return info;
}

} // namespace zovex

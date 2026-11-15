#include "probes.h"
#include "log.h"

#include <fcntl.h>
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
                      "). The runtime must use the proot path on this device.";
        ZLOGW("W^X probe: PROT_EXEC denied in %s: %s", dir.c_str(), std::strerror(errno));
        ::close(fd);
        ::unlink(info.path.c_str());
        return info;
    }

    ::munmap(map, pageSize);
    ::close(fd);
    ::unlink(info.path.c_str());

    info.ok = true;
    info.detail = "Executable mappings allowed; the runtime can exec directly.";
    ZLOGI("W^X probe: PROT_EXEC allowed in %s", dir.c_str());
    return info;
}

} // namespace zovex

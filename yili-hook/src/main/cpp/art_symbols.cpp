#include "art_symbols.h"

#include <android/log.h>
#include <elf.h>
#include <fcntl.h>
#include <link.h>
#include <sys/mman.h>
#include <sys/stat.h>
#include <unistd.h>

#include <cstring>
#include <vector>

extern "C" {
#include "xz.h"
}

#define LOG_TAG "YiliHook"
#define LOGE(...) __android_log_print(ANDROID_LOG_ERROR, LOG_TAG, __VA_ARGS__)
#define LOGI(...) __android_log_print(ANDROID_LOG_INFO, LOG_TAG, __VA_ARGS__)

namespace yili {
namespace {

#if defined(__LP64__)
using Ehdr = Elf64_Ehdr;
using Shdr = Elf64_Shdr;
using Sym = Elf64_Sym;
constexpr unsigned char kClass = ELFCLASS64;
#else
using Ehdr = Elf32_Ehdr;
using Shdr = Elf32_Shdr;
using Sym = Elf32_Sym;
constexpr unsigned char kClass = ELFCLASS32;
#endif

struct Found {
    std::string path;
    uintptr_t bias = 0;
};

int FindArt(dl_phdr_info *info, size_t, void *data) {
    if (info->dlpi_name == nullptr) return 0;
    std::string_view name(info->dlpi_name);
    constexpr std::string_view kSuffix = "/libart.so";
    if (name.size() >= kSuffix.size() && name.substr(name.size() - kSuffix.size()) == kSuffix) {
        auto *found = static_cast<Found *>(data);
        found->path = name;
        found->bias = info->dlpi_addr;
        return 1;
    }
    return 0;
}

bool InRange(size_t size, uint64_t off, uint64_t len) {
    return off <= size && len <= size - off;
}

// Decompresses a whole .xz stream. MiniDebugInfo is a few MB at most.
bool XzDecompress(const uint8_t *in, size_t in_size, std::vector<uint8_t> *out) {
    xz_crc32_init();
    xz_crc64_init();
    xz_dec *dec = xz_dec_init(XZ_DYNALLOC, 1u << 26);
    if (dec == nullptr) return false;
    out->resize(in_size * 4 + 4096);
    xz_buf buf{};
    buf.in = in;
    buf.in_size = in_size;
    buf.out = out->data();
    buf.out_size = out->size();
    xz_ret ret;
    while (true) {
        ret = xz_dec_run(dec, &buf);
        if (ret == XZ_OK && buf.out_pos == buf.out_size) {
            out->resize(out->size() * 2);
            buf.out = out->data();
            buf.out_size = out->size();
            continue;
        }
        if (ret != XZ_OK) break;
        if (buf.in_pos == buf.in_size) {  // truncated input
            ret = XZ_DATA_ERROR;
            break;
        }
    }
    xz_dec_end(dec);
    out->resize(buf.out_pos);
    return ret == XZ_STREAM_END;
}

}  // namespace

bool ArtSymbols::Load() {
    Found found;
    dl_iterate_phdr(FindArt, &found);
    if (found.path.empty()) {
        LOGE("libart.so is not loaded");
        return false;
    }
    path_ = found.path;
    bias_ = found.bias;

    int fd = open(path_.c_str(), O_RDONLY | O_CLOEXEC);
    if (fd < 0) {
        LOGE("cannot open %s", path_.c_str());
        return false;
    }
    struct stat st{};
    if (fstat(fd, &st) != 0 || st.st_size <= 0) {
        close(fd);
        return false;
    }
    size_t size = static_cast<size_t>(st.st_size);
    void *map = mmap(nullptr, size, PROT_READ, MAP_PRIVATE, fd, 0);
    close(fd);
    if (map == MAP_FAILED) {
        LOGE("cannot map %s", path_.c_str());
        return false;
    }
    bool ok = ReadElf(static_cast<const uint8_t *>(map), size, true);
    munmap(map, size);
    LOGI("%s: %zu symbols", path_.c_str(), symbols_.size());
    return ok && !symbols_.empty();
}

bool ArtSymbols::ReadElf(const uint8_t *data, size_t size, bool allow_debugdata) {
    if (size < sizeof(Ehdr) || memcmp(data, ELFMAG, SELFMAG) != 0 || data[EI_CLASS] != kClass) {
        LOGE("not an ELF of this ABI");
        return false;
    }
    auto *eh = reinterpret_cast<const Ehdr *>(data);
    if (eh->e_shentsize != sizeof(Shdr) ||
        !InRange(size, eh->e_shoff, static_cast<uint64_t>(eh->e_shnum) * sizeof(Shdr)) ||
        eh->e_shstrndx >= eh->e_shnum) {
        LOGE("bad section headers");
        return false;
    }
    auto *sh = reinterpret_cast<const Shdr *>(data + eh->e_shoff);
    const Shdr &names = sh[eh->e_shstrndx];
    if (!InRange(size, names.sh_offset, names.sh_size)) return false;
    auto *shstr = reinterpret_cast<const char *>(data + names.sh_offset);

    for (int i = 0; i < eh->e_shnum; i++) {
        const Shdr &s = sh[i];
        if ((s.sh_type == SHT_DYNSYM || s.sh_type == SHT_SYMTAB) && s.sh_link < eh->e_shnum) {
            AddTable(data, size, &s, &sh[s.sh_link]);
        } else if (allow_debugdata && s.sh_type == SHT_PROGBITS && s.sh_name < names.sh_size &&
                   strcmp(shstr + s.sh_name, ".gnu_debugdata") == 0 &&
                   InRange(size, s.sh_offset, s.sh_size)) {
            std::vector<uint8_t> elf;
            if (XzDecompress(data + s.sh_offset, s.sh_size, &elf)) {
                ReadElf(elf.data(), elf.size(), false);
            } else {
                LOGE("cannot decompress .gnu_debugdata");
            }
        }
    }
    return true;
}

void ArtSymbols::AddTable(const uint8_t *data, size_t size, const void *shdr_sym,
                          const void *shdr_str) {
    auto *ss = static_cast<const Shdr *>(shdr_sym);
    auto *st = static_cast<const Shdr *>(shdr_str);
    if (ss->sh_entsize != sizeof(Sym) || !InRange(size, ss->sh_offset, ss->sh_size) ||
        !InRange(size, st->sh_offset, st->sh_size) || st->sh_size == 0) {
        return;
    }
    auto *syms = reinterpret_cast<const Sym *>(data + ss->sh_offset);
    auto *strs = reinterpret_cast<const char *>(data + st->sh_offset);
    size_t count = ss->sh_size / sizeof(Sym);
    for (size_t i = 0; i < count; i++) {
        const Sym &sym = syms[i];
        if (sym.st_shndx == SHN_UNDEF || sym.st_value == 0 || sym.st_name >= st->sh_size) continue;
        unsigned type = sym.st_info & 0xf;
        if (type != STT_FUNC && type != STT_OBJECT) continue;
        const char *name = strs + sym.st_name;
        size_t max = st->sh_size - sym.st_name;
        size_t len = strnlen(name, max);
        if (len == 0 || len == max) continue;
        symbols_.try_emplace(std::string(name, len), bias_ + sym.st_value);
    }
}

void *ArtSymbols::Find(std::string_view name) const {
    auto it = symbols_.find(std::string(name));
    return it == symbols_.end() ? nullptr : reinterpret_cast<void *>(it->second);
}

void *ArtSymbols::FindPrefix(std::string_view prefix) const {
    if (void *exact = Find(prefix)) return exact;
    const std::string *best = nullptr;
    uintptr_t addr = 0;
    for (const auto &[name, value] : symbols_) {
        if (name.size() > prefix.size() && name.compare(0, prefix.size(), prefix) == 0 &&
            (best == nullptr || name < *best)) {
            best = &name;
            addr = value;
        }
    }
    return reinterpret_cast<void *>(addr);
}

void ArtSymbols::Clear() {
    symbols_.clear();
    symbols_.rehash(0);
}

}  // namespace yili

// Resolves symbols of the loaded libart.so, including hidden ones.
//
// libart exports few of the symbols LSPlant needs. The rest are in .symtab, which Android
// strips into MiniDebugInfo: an xz-compressed ELF in the .gnu_debugdata section. This reader
// maps the on-disk file, collects .dynsym, .symtab and the MiniDebugInfo .symtab, and turns
// st_value into a runtime address with the load bias reported by dl_iterate_phdr.
#pragma once

#include <cstdint>
#include <string>
#include <string_view>
#include <unordered_map>

namespace yili {

class ArtSymbols {
public:
    // Finds libart.so among loaded libraries and reads its symbols. False on failure.
    bool Load();

    // Address of the exact symbol, or nullptr.
    void *Find(std::string_view name) const;

    // Address of the first symbol (by name order) starting with prefix, or nullptr.
    // Some ART functions carry compiler suffixes (".__uniq.123", LTO clones).
    void *FindPrefix(std::string_view prefix) const;

    // Frees the tables; Find* return nullptr afterwards.
    void Clear();

    const std::string &path() const { return path_; }
    size_t size() const { return symbols_.size(); }

private:
    bool ReadElf(const uint8_t *data, size_t size, bool allow_debugdata);
    void AddTable(const uint8_t *data, size_t size, const void *shdr_sym, const void *shdr_str);

    std::string path_;
    uintptr_t bias_ = 0;
    std::unordered_map<std::string, uintptr_t> symbols_;
};

}  // namespace yili

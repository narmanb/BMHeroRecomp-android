#pragma once
#include <filesystem>

namespace bmhero::android {
template<class Configuration>
void configure_rt64_storage(Configuration& configuration, const std::filesystem::path& app_directory) {
    // Android's passwd/home fallback points outside this app's writable sandbox.
    configuration.detectDataPath = false;
    configuration.dataPath = app_directory / "rt64";
}
}

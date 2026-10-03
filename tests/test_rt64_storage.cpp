#include "android_rt64_storage.h"
#include "common/rt64_user_paths.h"
#include <fstream>
#include <iostream>

// RT64's configuration boundary; field types/defaults match ApplicationConfiguration.
struct Configuration {
    std::filesystem::path appId = "rt64";
    std::filesystem::path dataPath;
    bool detectDataPath = true;
};

int main(int argc, char** argv) {
    if (argc != 2) return 2;
    const std::filesystem::path app_directory = argv[1];
    Configuration configuration;
    bmhero::android::configure_rt64_storage(configuration, app_directory);
    RT64::UserPaths paths;
    // Apply RT64's actual detection override, then its real path setup implementation.
    paths.setupPaths(configuration.detectDataPath ? paths.detectDataPath(configuration.appId) : configuration.dataPath);
    if (paths.dataPath != app_directory / "rt64") {
        std::cerr << "RT64 escaped app storage: " << paths.dataPath << '\n';
        return 1;
    }
    std::filesystem::create_directories(paths.dataPath);
    std::ofstream log(paths.logPath);
    log << "renderer log";
    log.close();
    if (!log || !std::filesystem::is_regular_file(app_directory / "rt64" / "rt64.log")) return 1;
    std::cout << "RT64 creates its log inside app-private storage\n";
}

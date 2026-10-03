#include "file.h"
#include "android_support.h"
#include <SDL.h>
#include <cstdlib>
#include <stdexcept>
namespace recompui::file {
std::filesystem::path get_app_folder_path() {
    const char* path=std::getenv("APP_FOLDER_PATH");
    if(!path || !*path)throw std::runtime_error("Android app path missing");
    return path;
}
std::filesystem::path get_program_path() {return get_app_folder_path();}
std::filesystem::path get_asset_path(const char* asset) {return get_program_path()/"assets"/asset;}
void open_file_dialog(std::function<void(bool,const std::filesystem::path&)> callback) {
    bmhero::android::open_document(false,[callback](bool ok,const std::list<std::filesystem::path>& paths) {
        callback(ok && !paths.empty(),paths.empty()?std::filesystem::path{}:paths.front());
    });
}
void open_file_dialog_multiple(std::function<void(bool,const std::list<std::filesystem::path>&)> callback) {
    bmhero::android::open_document(true,std::move(callback));
}
void show_error_message_box(const char* title,const char* message) {SDL_ShowSimpleMessageBox(SDL_MESSAGEBOX_ERROR,title,message,nullptr);}
}

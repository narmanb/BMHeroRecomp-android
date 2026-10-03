#pragma once
#include <filesystem>
#include <functional>
#include <list>
namespace bmhero::android {
    void startup_stage(const char* stage);
    void initialize();
    void open_document(bool multiple, std::function<void(bool, const std::list<std::filesystem::path>&)> callback);
    void dispatch_document_result();
    void publish_window(void* window);
    void* take_window();
}

#pragma once
namespace bmhero::android {
// Plume's Android setRenderWindow consumes the reference acquired by the host.
template<class Window, class SwapChain, class Release>
void handoff_window(Window* owned, SwapChain* recipient, Release release) {
    if (recipient) recipient->setRenderWindow(owned);
    else release(owned);
}
}

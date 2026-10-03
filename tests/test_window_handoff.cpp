#include "android_window_handoff.h"
#include <cassert>
struct Window {int refs=1;};
struct SwapChain {Window* queued=nullptr;void setRenderWindow(Window* w){queued=w;}};
int main() {
 auto release=[](Window* window){--window->refs;};
 Window accepted;SwapChain chain;
 bmhero::android::handoff_window(&accepted,&chain,release);
 assert(chain.queued==&accepted && accepted.refs==1);
 release(chain.queued);assert(accepted.refs==0);
 Window rejected;
 bmhero::android::handoff_window(&rejected,static_cast<SwapChain*>(nullptr),release);
 assert(rejected.refs==0);
}

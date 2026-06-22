#![no_std]

use core::ffi::{c_int, c_void};
use core::panic::PanicInfo;

type Ptr = *mut c_void;

extern "C" {
    fn renderer_impl_init(env: Ptr);
    fn renderer_impl_set_filtering(env: Ptr, this: Ptr, filtering: c_int);
    fn renderer_impl_test_capabilities(legacy_drawing: *mut c_int);
    fn renderer_impl_set_window(env: Ptr, this: Ptr, surface: Ptr);
    fn renderer_impl_set_viewport(env: Ptr, class: Ptr, x: c_int, y: c_int, w: c_int, h: c_int, ew: c_int, eh: c_int);
    fn renderer_impl_set_zoom(env: Ptr, class: Ptr, percent: c_int);
    fn renderer_impl_set_shared_state(state: Ptr);
    fn renderer_impl_add_buffer(buffer: Ptr);
    fn renderer_impl_remove_buffer(id: u64);
    fn renderer_impl_remove_all_buffers();
}

#[no_mangle]
pub unsafe extern "C" fn rendererInit(env: Ptr) {
    renderer_impl_init(env)
}

#[no_mangle]
pub unsafe extern "C" fn rendererSetFiltering(env: Ptr, this: Ptr, filtering: c_int) {
    renderer_impl_set_filtering(env, this, filtering)
}

#[no_mangle]
pub unsafe extern "C" fn rendererTestCapabilities(legacy_drawing: *mut c_int) {
    renderer_impl_test_capabilities(legacy_drawing)
}

#[no_mangle]
pub unsafe extern "C" fn rendererSetWindow(env: Ptr, this: Ptr, surface: Ptr) {
    renderer_impl_set_window(env, this, surface)
}

#[no_mangle]
pub unsafe extern "C" fn rendererSetViewport(env: Ptr, class: Ptr, x: c_int, y: c_int, w: c_int, h: c_int, ew: c_int, eh: c_int) {
    renderer_impl_set_viewport(env, class, x, y, w, h, ew, eh)
}

#[no_mangle]
pub unsafe extern "C" fn rendererSetZoom(env: Ptr, class: Ptr, percent: c_int) {
    renderer_impl_set_zoom(env, class, percent)
}

#[no_mangle]
pub unsafe extern "C" fn rendererSetSharedState(state: Ptr) {
    renderer_impl_set_shared_state(state)
}

#[no_mangle]
pub unsafe extern "C" fn rendererAddBuffer(buffer: Ptr) {
    renderer_impl_add_buffer(buffer)
}

#[no_mangle]
pub unsafe extern "C" fn rendererRemoveBuffer(id: u64) {
    renderer_impl_remove_buffer(id)
}

#[no_mangle]
pub unsafe extern "C" fn rendererRemoveAllBuffers() {
    renderer_impl_remove_all_buffers()
}

#[panic_handler]
fn panic(_: &PanicInfo) -> ! {
    loop {}
}

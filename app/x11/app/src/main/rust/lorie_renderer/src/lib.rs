#![no_std]

use core::ffi::{c_char, c_int, c_void};
use core::panic::PanicInfo;
use core::{mem, ptr, slice, str};

type Ptr = *mut c_void;
type Size = usize;
type SSize = isize;

unsafe extern "C" {
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
    fn sendmsg(fd: c_int, msg: *const MsgHdr, flags: c_int) -> SSize;
    fn recvmsg(fd: c_int, msg: *mut MsgHdr, flags: c_int) -> SSize;
}

#[repr(C)]
struct IoVec {
    iov_base: Ptr,
    iov_len: Size,
}

#[repr(C)]
struct MsgHdr {
    msg_name: Ptr,
    msg_namelen: u32,
    msg_iov: *mut IoVec,
    msg_iovlen: Size,
    msg_control: Ptr,
    msg_controllen: Size,
    msg_flags: c_int,
}

#[repr(C)]
struct CMsgHdr {
    cmsg_len: Size,
    cmsg_level: c_int,
    cmsg_type: c_int,
}

const SOL_SOCKET: c_int = 1;
const SCM_RIGHTS: c_int = 1;

const fn align(n: Size) -> Size {
    (n + mem::size_of::<Size>() - 1) & !(mem::size_of::<Size>() - 1)
}

unsafe fn cmsg_data(cmsg: *mut CMsgHdr) -> *mut c_int {
    unsafe { (cmsg as *mut u8).add(align(mem::size_of::<CMsgHdr>())) as *mut c_int }
}

#[unsafe(no_mangle)]
pub unsafe extern "C" fn rendererInit(env: Ptr) {
    unsafe { renderer_impl_init(env) }
}

#[unsafe(no_mangle)]
pub unsafe extern "C" fn rendererSetFiltering(env: Ptr, this: Ptr, filtering: c_int) {
    unsafe { renderer_impl_set_filtering(env, this, filtering) }
}

#[unsafe(no_mangle)]
pub unsafe extern "C" fn rendererTestCapabilities(legacy_drawing: *mut c_int) {
    unsafe { renderer_impl_test_capabilities(legacy_drawing) }
}

#[unsafe(no_mangle)]
pub unsafe extern "C" fn rendererSetWindow(env: Ptr, this: Ptr, surface: Ptr) {
    unsafe { renderer_impl_set_window(env, this, surface) }
}

#[unsafe(no_mangle)]
pub unsafe extern "C" fn rendererSetViewport(env: Ptr, class: Ptr, x: c_int, y: c_int, w: c_int, h: c_int, ew: c_int, eh: c_int) {
    unsafe { renderer_impl_set_viewport(env, class, x, y, w, h, ew, eh) }
}

#[unsafe(no_mangle)]
pub unsafe extern "C" fn rendererSetZoom(env: Ptr, class: Ptr, percent: c_int) {
    unsafe { renderer_impl_set_zoom(env, class, percent) }
}

#[unsafe(no_mangle)]
pub unsafe extern "C" fn rendererSetSharedState(state: Ptr) {
    unsafe { renderer_impl_set_shared_state(state) }
}

#[unsafe(no_mangle)]
pub unsafe extern "C" fn rendererAddBuffer(buffer: Ptr) {
    unsafe { renderer_impl_add_buffer(buffer) }
}

#[unsafe(no_mangle)]
pub unsafe extern "C" fn rendererRemoveBuffer(id: u64) {
    unsafe { renderer_impl_remove_buffer(id) }
}

#[unsafe(no_mangle)]
pub unsafe extern "C" fn rendererRemoveAllBuffers() {
    unsafe { renderer_impl_remove_all_buffers() }
}

#[unsafe(no_mangle)]
pub unsafe extern "C" fn lorie_rs_ancil_send_fd(sock: c_int, fd: c_int) -> c_int {
    let mut byte = b'!';
    let mut iov = IoVec { iov_base: &mut byte as *mut u8 as Ptr, iov_len: 1 };
    let mut control = [0usize; 4];
    let cmsg = control.as_mut_ptr() as *mut CMsgHdr;
    unsafe {
        (*cmsg).cmsg_len = align(mem::size_of::<CMsgHdr>()) + mem::size_of::<c_int>();
        (*cmsg).cmsg_level = SOL_SOCKET;
        (*cmsg).cmsg_type = SCM_RIGHTS;
        *cmsg_data(cmsg) = fd;
    }
    let msg = MsgHdr {
        msg_name: ptr::null_mut(),
        msg_namelen: 0,
        msg_iov: &mut iov,
        msg_iovlen: 1,
        msg_control: control.as_mut_ptr() as Ptr,
        msg_controllen: align(mem::size_of::<CMsgHdr>()) + mem::size_of::<c_int>(),
        msg_flags: 0,
    };
    if unsafe { sendmsg(sock, &msg, 0) } >= 0 { 0 } else { -1 }
}

#[unsafe(no_mangle)]
pub unsafe extern "C" fn lorie_rs_ancil_recv_fd(sock: c_int) -> c_int {
    let mut byte = 0u8;
    let mut iov = IoVec { iov_base: &mut byte as *mut u8 as Ptr, iov_len: 1 };
    let mut control = [0usize; 4];
    let cmsg = control.as_mut_ptr() as *mut CMsgHdr;
    unsafe {
        (*cmsg).cmsg_len = align(mem::size_of::<CMsgHdr>()) + mem::size_of::<c_int>();
        (*cmsg).cmsg_level = SOL_SOCKET;
        (*cmsg).cmsg_type = SCM_RIGHTS;
        *cmsg_data(cmsg) = -1;
    }
    let mut msg = MsgHdr {
        msg_name: ptr::null_mut(),
        msg_namelen: 0,
        msg_iov: &mut iov,
        msg_iovlen: 1,
        msg_control: control.as_mut_ptr() as Ptr,
        msg_controllen: align(mem::size_of::<CMsgHdr>()) + mem::size_of::<c_int>(),
        msg_flags: 0,
    };
    if unsafe { recvmsg(sock, &mut msg, 0) } < 0 {
        -1
    } else {
        unsafe { *cmsg_data(cmsg) }
    }
}

#[unsafe(no_mangle)]
pub unsafe extern "C" fn lorie_rs_check_utf8(data: *const c_char, len: Size) -> bool {
    if data.is_null() {
        return false;
    }
    str::from_utf8(unsafe { slice::from_raw_parts(data as *const u8, len) }).is_ok()
}

#[panic_handler]
fn panic(_: &PanicInfo) -> ! {
    loop {}
}

#[unsafe(no_mangle)]
pub extern "C" fn rust_eh_personality() {}

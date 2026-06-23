#![no_std]

use core::{ffi::{c_char, c_int, c_void}, mem, panic::PanicInfo, ptr, slice, str};

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
    fn abort() -> !;
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
const CMSG_LEN: Size = align(mem::size_of::<CMsgHdr>()) + mem::size_of::<c_int>();

const fn align(n: Size) -> Size {
    (n + mem::size_of::<Size>() - 1) & !(mem::size_of::<Size>() - 1)
}

unsafe fn cmsg_data(cmsg: *mut CMsgHdr) -> *mut c_int {
    unsafe { (cmsg.cast::<u8>()).add(align(mem::size_of::<CMsgHdr>())).cast() }
}

unsafe fn cmsg(control: &mut [usize; 4], fd: c_int) -> *mut CMsgHdr {
    let p = control.as_mut_ptr().cast::<CMsgHdr>();
    unsafe {
        *p = CMsgHdr { cmsg_len: CMSG_LEN, cmsg_level: SOL_SOCKET, cmsg_type: SCM_RIGHTS };
        *cmsg_data(p) = fd;
    }
    p
}

macro_rules! fwd {
    ($name:ident($($arg:ident:$typ:ty),*)=>$target:ident) => {
        #[unsafe(no_mangle)]
        pub unsafe extern "C" fn $name($($arg:$typ),*) { unsafe { $target($($arg),*) } }
    };
}

fwd!(rendererInit(env: Ptr) => renderer_impl_init);
fwd!(rendererSetFiltering(env: Ptr, this: Ptr, filtering: c_int) => renderer_impl_set_filtering);
fwd!(rendererTestCapabilities(legacy_drawing: *mut c_int) => renderer_impl_test_capabilities);
fwd!(rendererSetWindow(env: Ptr, this: Ptr, surface: Ptr) => renderer_impl_set_window);
fwd!(rendererSetViewport(env: Ptr, class: Ptr, x: c_int, y: c_int, w: c_int, h: c_int, ew: c_int, eh: c_int) => renderer_impl_set_viewport);
fwd!(rendererSetZoom(env: Ptr, class: Ptr, percent: c_int) => renderer_impl_set_zoom);
fwd!(rendererSetSharedState(state: Ptr) => renderer_impl_set_shared_state);
fwd!(rendererAddBuffer(buffer: Ptr) => renderer_impl_add_buffer);
fwd!(rendererRemoveBuffer(id: u64) => renderer_impl_remove_buffer);
fwd!(rendererRemoveAllBuffers() => renderer_impl_remove_all_buffers);

#[unsafe(no_mangle)]
pub unsafe extern "C" fn lorie_rs_ancil_send_fd(sock: c_int, fd: c_int) -> c_int {
    let mut byte = b'!';
    let mut iov = IoVec { iov_base: &mut byte as *mut u8 as Ptr, iov_len: 1 };
    let mut control = [0usize; 4];
    unsafe { cmsg(&mut control, fd) };
    let msg = MsgHdr {
        msg_name: ptr::null_mut(),
        msg_namelen: 0,
        msg_iov: &mut iov,
        msg_iovlen: 1,
        msg_control: control.as_mut_ptr() as Ptr,
        msg_controllen: CMSG_LEN,
        msg_flags: 0,
    };
    if unsafe { sendmsg(sock, &msg, 0) } >= 0 { 0 } else { -1 }
}

#[unsafe(no_mangle)]
pub unsafe extern "C" fn lorie_rs_ancil_recv_fd(sock: c_int) -> c_int {
    let mut byte = 0u8;
    let mut iov = IoVec { iov_base: &mut byte as *mut u8 as Ptr, iov_len: 1 };
    let mut control = [0usize; 4];
    let cmsg = unsafe { cmsg(&mut control, -1) };
    let mut msg = MsgHdr {
        msg_name: ptr::null_mut(),
        msg_namelen: 0,
        msg_iov: &mut iov,
        msg_iovlen: 1,
        msg_control: control.as_mut_ptr() as Ptr,
        msg_controllen: CMSG_LEN,
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
    unsafe { abort() }
}

#[unsafe(no_mangle)]
pub extern "C" fn rust_eh_personality() {}

let s:plugin_dir = expand('<sfile>:p:h:h')
let s:jar_path = join([s:plugin_dir, 'target', 'uberjar', 'vim-clj.jar'], '/')

if ! exists('g:vim_clj_is_running') | let g:vim_clj_is_running = 0 | endif
if ! exists('g:vim_clj_uberjar') | let g:vim_clj_uberjar = 0 | endif
if ! exists('g:vim_clj_channel') | let g:vim_clj_channel = 0 | endif

function! vim_clj#is_running()
  return g:vim_clj_is_running
endfunc

function! vim_clj#on_exit()
  echo 'VimNrepl exited'
endfunc

function! vim_clj#start()
  if vim_clj_is_running() | return | endif

  if g:vim_clj_uberjar
    let g:vim_clj_channel = jobstart(['java', '-jar', s:jar_path], {'rpc': v:true, 'on_exit': function('VimNreplExit')})
  else
    let cmd = 'cd ' . s:plugin_dir . ' && lein run'
    let g:vim_clj_channel = jobstart(cmd, {'rpc': v:true, 'on_exit': function('VimNreplExit')})
  endif

  if g:vim_clj_channel <= 0
    echo 'VimNrepl was not started'
  endif
endfunc

function! vim_clj#request(cmd, ...)
  if g:vim_clj_is_running
    return call(function('rpcrequest'), [g:vim_clj_channel, a:cmd] + a:000)
  endif
endfunc

function! vim_clj#notify(cmd, ...)
  if g:vim_clj_is_running
    return call(function('rpcnotify'), [g:vim_clj_channel, a:cmd] + a:000)
  endif
endfunc

function! vim_clj#stop()
  let g:vim_clj_is_running = 0
  call VimNreplRequest('shutdown')
endfunc

function! vim_clj#ns()
  return vim_clj#request('clj-file-ns', expand('%'))
endfunc

function! vim_clj#format_code(code)
  let result = vim_clj#request('format-code', a:code)

  if result != v:null
    return result
  endif
endfunc

function! vim_clj#ns_eval(ns, code)
  return vim_clj#request('ns-eval', getcwd(), a:ns, a:code)
endfunc

function! vim_clj#connect_nrepl(conn_string)
  call vim_clj#notify('connect-nrepl', join([a:conn_string, getcwd()], ' '))
endfunc

command! -bar VimCljStart call vim_clj#start()
command! -bar VimCljStop call vim_clj#stop()
command! -nargs=1 VimCljConnect call vim_clj#connect_nrepl('<args>')

augroup vim_clj
  au!
  au VimLeave * call vim_clj#stop()
augroup END

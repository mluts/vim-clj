let s:plugin_dir = expand('<sfile>:p:h:h')
let s:jar_path = join([s:plugin_dir, 'target', 'uberjar', 'vim-clj.jar'], '/')

if ! exists('g:vim_clj_is_running') | let g:vim_clj_is_running = 0 | endif
if ! exists('g:vim_clj_uberjar') | let g:vim_clj_uberjar = 0 | endif
if ! exists('g:vim_clj_channel') | let g:vim_clj_channel = 0 | endif

function! vim_clj#is_running()
  return g:vim_clj_is_running
endfunc

function! vim_clj#on_exit(...)
  echo 'VimClj exited'
endfunc

function! vim_clj#start()
  if vim_clj#is_running() | return | endif

  if g:vim_clj_uberjar
    let g:vim_clj_channel = jobstart(['java', '-jar', s:jar_path], {'rpc': v:true, 'on_exit': function('vim_clj#on_exit')})
  else
    let cmd = 'cd ' . s:plugin_dir . ' && lein run'
    let g:vim_clj_channel = jobstart(cmd, {'rpc': v:true, 'on_exit': function('vim_clj#on_exit')})
  endif

  if g:vim_clj_channel <= 0
    echo 'VimClj was not started'
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
  call vim_clj#request('shutdown')
endfunc

function! vim_clj#ns()
  return vim_clj#request('clj-file-ns', expand('%:p'))
endfunc

function! vim_clj#format_code(lnum, lcount)
  call vim_clj#request('format-code', a:lnum, a:lcount)
endfunc

function! vim_clj#ns_eval(ns, code)
  return vim_clj#request('ns-eval', getcwd(), a:ns, a:code)
endfunc

function! vim_clj#connect_nrepl(conn_string)
  call vim_clj#notify('connect-nrepl', join([a:conn_string, getcwd()], ' '))
endfunc

function! vim_clj#doc(symbol)
  call vim_clj#request('symbol-info', getcwd(), vim_clj#ns(), a:symbol)
endfunc

function! vim_clj#nrepl_eval_cmdline()
  let s:input = 1

  try
    return vim_clj#request('nrepl-eval-cmdline', getcwd(), vim_clj#ns())
  finally
    unlet! s:input
  endtry
endfunc

command! -bar VimCljStart call vim_clj#start()
command! -bar VimCljStop call vim_clj#stop()
command! -nargs=1 VimCljConnect call vim_clj#connect_nrepl('<args>')
command! -nargs=1 VimCljDoc call vim_clj#doc('<args>')
command! -nargs=0 VimCljEvalCmdline call vim_clj#nrepl_eval_cmdline()
command! -nargs=0 VimCljIsRunning echo vim_clj#is_running()

function! s:cmdwinenter() abort
  setlocal filetype=clojure
endfunction

function! s:cmdwinleave() abort
  setlocal filetype< omnifunc<
endfunction

function! vim_clj#ping()
  echo vim_clj#request('ping')
endfunc

nnoremap <silent> <Plug>VimCljJump :call vim_clj#request('jump-to-symbol')<CR>
nnoremap <silent> <Plug>VimCljEvalCmdline :call vim_clj#request('nrepl-eval-cmdline')<CR>

function! s:setup_mappings()
  nmap <buffer> cqc     <Plug>VimCljEvalCmdline
  nmap <buffer> [<C-D>  <Plug>VimCljJump
endfunc

augroup vim_clj
  au!
  au VimLeave * call vim_clj#stop()
  au FileType clojure setlocal keywordprg=:VimCljDoc
  au FileType clojure setlocal formatexpr=vim_clj#format_code(v:lnum,v:count)
  au FileType clojure call s:setup_mappings()
  au CmdWinEnter @ if exists('s:input') | call s:cmdwinenter() | endif
  au CmdWinLeave @ if exists('s:input') | call s:cmdwinleave() | endif
augroup END

<script>
(function(){
  let chain=Promise.resolve();
  async function exportBlob(blob,name){
    let id;
    try {
      id=NativeExport.begin(name);
      for(let off=0;off<blob.size;off+=49152){
        const bytes=new Uint8Array(await blob.slice(off,off+49152).arrayBuffer());
        let text='';for(let i=0;i<bytes.length;i++)text+=String.fromCharCode(bytes[i]);
        if(!NativeExport.append(id,btoa(text)))throw Error('写入临时文件失败');
      }
      NativeExport.finish(id);
    }catch(e){if(id)NativeExport.abort(id);alert('准备保存失败：'+e.message)}
  }
  document.addEventListener('click',function(e){
    const a=e.target.closest('a[download]');
    if(!a||!a.href.startsWith('blob:'))return;
    e.preventDefault();
    const url=a.href,name=a.download;
    // Start reading immediately, before the page revokes its temporary Blob URL.
    const pending=fetch(url).then(r=>r.blob());
    pending.catch(()=>{});
    chain=chain.then(()=>pending).then(blob=>exportBlob(blob,name)).catch(e=>alert('导出失败：'+e.message));
  },true);
  try{new DecompressionStream('deflate-raw');new TransformStream();new TextDecoder('gb18030');}
  catch(e){NativeExport.incompatible()}
})();
</script>

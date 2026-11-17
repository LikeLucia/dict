const fs=require('node:fs');
const vm=require('node:vm');
const assert=require('node:assert/strict');
const path=require('node:path');
const code=fs.readFileSync(path.join(__dirname,'android/bridge.js'),'utf8').replace('<script>','').replace('</script>','');
function host(failWrite=false){
  let click,seq=0;
  const items=new Map(),outputs=[],aborted=[],alerts=[];
  const ctx={Uint8Array,String,TextDecoder,TransformStream,DecompressionStream,btoa,Promise,
    alert:s=>alerts.push(s),fetch:async url=>({blob:async()=>new Blob([url==='blob:empty'?Buffer.alloc(0):Buffer.from(Array.from({length:120000},(_,i)=>i%256))])}),
    document:{addEventListener:(event,cb)=>{click=cb;}},
    NativeExport:{begin:name=>{const id=String(++seq);items.set(id,{name,parts:[]});return id;},
      append:(id,data)=>{if(failWrite)return false;items.get(id).parts.push(Buffer.from(data,'base64'));return true;},
      finish:id=>{const item=items.get(id);outputs.push({name:item.name,data:Buffer.concat(item.parts),chunks:item.parts.length});items.delete(id);},
      abort:id=>{aborted.push(id);items.delete(id);},incompatible:()=>alerts.push('incompatible')}};
  vm.runInNewContext(code,ctx);
  return {outputs,aborted,alerts,click(url,name){let prevented=false;click({target:{closest:()=>({href:url,download:name})},preventDefault:()=>{prevented=true;}});return prevented;}};
}
async function drainUntil(fn){for(let i=0;i<200;i++){if(fn())return;await new Promise(resolve=>setImmediate(resolve));}throw Error('bridge did not complete');}
(async()=>{
 const h=host();assert(h.click('blob:all','词库.txt'));assert(h.click('blob:empty','空.txt'));
 await drainUntil(()=>h.outputs.length===2);
 assert.equal(h.outputs[0].chunks,3);assert.equal(h.outputs[1].chunks,0);
 assert(h.outputs[0].data.equals(Buffer.from(Array.from({length:120000},(_,i)=>i%256))));
 assert.deepEqual(h.outputs.map(x=>x.name),['词库.txt','空.txt']);assert.deepEqual(h.alerts,[]);
 assert.equal(h.click('https://example.invalid/','x'),false);
 const failure=host(true);failure.click('blob:all','x');await drainUntil(()=>failure.alerts.length===1);
 assert.equal(failure.aborted.length,1);assert.equal(failure.outputs.length,0);
 console.log('PASS: 120000 bytes/all byte values, three chunks, empty export, ordered multi-export, non-Blob ignored, failed-write abort. Node simulation; Android SAF not tested.');
})().catch(e=>{console.error(e);process.exitCode=1;});

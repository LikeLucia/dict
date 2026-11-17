const fs = require('node:fs');
const vm = require('node:vm');
const assert = require('node:assert/strict');
const {deflateRawSync} = require('node:zlib');
const path = require('node:path');

const source = process.argv[2] || path.join(__dirname, '通用词库刷辅助码.html');
const fixturePath = path.join(__dirname, 'tests/mixed-layout.dict.yaml');
const html = fs.readFileSync(source, 'utf8');
const scripts = [...html.matchAll(/<script\b([^>]*)>([\s\S]*?)<\/script>/gi)];
const elements = new Map();
const downloads = [];
function element(id) {
  if (!elements.has(id)) elements.set(id, {value:'', checked:false, textContent:'', className:'', style:{},
    classList:{add(){},remove(){}}, addEventListener(){}, appendChild(){}, click(){}, remove(){}});
  return elements.get(id);
}
const defaults = {mode:'autoAux', auxType:'yuanmo', toneType:'symbol', sep:'tab', enc:'UTF-8', wordCol:'1', pyCol:'2', auxSep:';'};
for (const [k,v] of Object.entries(defaults)) element(k).value = v;
element('replace').checked = true;
const ctx = vm.createContext({console, Blob, Response, TextDecoder, TextEncoder, DecompressionStream, CompressionStream,
  TransformStream, ReadableStream, Uint8Array, Uint32Array, DataView, atob, btoa, setTimeout, clearTimeout, URL,
  document:{getElementById:element, createElement:()=>element('a'+Math.random()), body:{appendChild(){}}}});
for (const m of scripts) {
  if (/type="text\/plain"/.test(m[1])) element(m[1].match(/id="([^"]+)"/)[1]).textContent = m[2];
  else vm.runInContext(m[2], ctx, {filename:source});
}
const api = vm.runInContext('({ready, tables, buildEntry, state, convertLine, Zip, crcAdd, readZipEntries, checkedStream, run, getOpt, pinyinPro})', ctx);
function opts(overrides={}) {return {...defaults, w:0,p:1,aux:';',encoding:'UTF-8',replace:true, table:api.tables.get('yuanmo'), ...overrides};}
async function convert(text, overrides={}) {
  const r = await api.buildEntry(new Blob([text]).stream(), opts(overrides));
  return {...r, text:new TextDecoder('utf-8',{ignoreBOM:true}).decode(await new Blob(r.parts).arrayBuffer())};
}
const header = '---\nname: sample\n...\n';
let passed = 0;
async function test(label, fn) {await fn(); passed++; console.log('PASS '+label);}
function bases(s) {return s.replace(/;[^\s'’]*/g, '');}
// 原 autoHeader 使用 join('\n')，既有的 CRLF 头部会转成 LF；词条正文另验字节。
function lines(s) {return s.split(/\r?\n/);}
(async()=>{
  await api.ready;
  const yui = fs.readFileSync(fixturePath, 'utf8').replace(/\r?\n/g, '\r\n');
  const missingCodeLine = lines(yui).indexOf('学习');
  const mixedTextLine = lines(yui).findIndex(line => line.startsWith('你好 世界\t'));
  assert(missingCodeLine >= 0 && mixedTextLine >= 0);
  await test('public fixture: default autoAux, all entries and original readings/weights retained', async()=>{
    const r = await convert(yui), before=lines(yui), after=lines(r.text);
    assert.equal(before.length, after.length);
    for(let i=0;i<before.length;i++) {
      if(i<5 || !before[i].trim() || before[i].startsWith('#') || i===mixedTextLine) {assert.equal(after[i],before[i]);continue;}
      const a=before[i].split('\t'),b=after[i].split('\t');
      assert.equal(b[0],a[0]);
      if(a.length>1) {assert.equal(bases(b[1]),bases(a[1]));assert.equal(b.length,a.length);if(a.length>2)assert.equal(b[2],a[2]);}
      else {assert.equal(b.length,3);assert.equal(b[2],'0');assert.match(b[1],/;/);}
      const expectedCodes=Array.from(a[0]).map(c=>api.tables.get('yuanmo').get(c));
      assert.deepEqual(b[1].match(/[^\s'’]+/g).map(t=>t.split(';')[1]),expectedCodes);
    }
    assert.equal(r.st.coded,7);
    assert.equal(r.st.counts['混合非汉字'],1);
    console.log('  fixture processed='+r.st.coded+', counts='+JSON.stringify(r.st.counts));
  });
  await test('public fixture: autoOnly retains existing codes and tones; fills only missing code',async()=>{
    const r=await convert(yui,{mode:'autoOnly'}), a=lines(yui), b=lines(r.text);
    a.forEach((l,i)=>{if(i!==missingCodeLine)assert.equal(b[i],l);});
    assert.equal(b[missingCodeLine], '学习\txué xí\t0');assert.equal(r.st.coded,1);
    const beforeBody=yui.slice(yui.indexOf('...')+3),afterBody=r.text.slice(r.text.indexOf('...')+3);
    assert.equal(afterBody,beforeBody.replace('学习\r\n','学习\txué xí\t0\r\n'));
  });
  await test('public fixture: existing mode unchanged; no invented pronunciation for absent code',async()=>{
    const r=await convert(yui,{mode:'existing'});
    assert.equal(r.st.coded,6);assert.equal(lines(r.text)[missingCodeLine],'学习');
    assert.equal(r.st.counts['无 code'],1);assert.equal(r.st.counts['混合非汉字'],1);
  });
  await test('public fixture: replace=false keeps all existing auxiliary codes including multi-code',async()=>{
    const r=await convert(yui,{replace:false}), a=lines(yui),b=lines(r.text);
    a.forEach((l,i)=>{if(i!==missingCodeLine)assert.equal(b[i],l);});assert.equal(r.st.coded,1);
  });
  await test('public fixture: all five tables, both tone modes, repeated conversion is idempotent',async()=>{
    for(const [auxType,table] of api.tables)for(const toneType of ['symbol','none']){
      const o={auxType,table,toneType};const a=await convert(yui,o),b=await convert(a.text,o);
      assert.equal(a.text,b.text);assert.equal(a.st.coded,7);
    }
  });
  await test('missing columns: mixed one/two/three fields, empty code, absent/empty/zero weight',async()=>{
    const input=header+'你好\n你好\t123\n你好\tnǐ hǎo\n你好\tnǐ hǎo\t900\n你好\t\t000\n你好\t\n你好\t\t\n';
    const r=await convert(input,{mode:'autoOnly'});
    assert.equal(r.text, header+'你好\tnǐ hǎo\t0\n你好\tnǐ hǎo\t123\n你好\tnǐ hǎo\n你好\tnǐ hǎo\t900\n你好\tnǐ hǎo\t000\n你好\tnǐ hǎo\n你好\tnǐ hǎo\t\n');
  });
  await test('default Rime uses text/code/weight independently of custom plain-text column settings',async()=>{
    const a=await convert(header+'你好\tnǐ hǎo\t123\n',{w:2,p:0});
    const b=await convert(header+'你好\tnǐ hǎo\t123\n');assert.equal(a.text,b.text);
  });
  await test('default Rime: numeric code with third weight is retained/reported, not auto-rewritten',async()=>{
    const r=await convert(header+'你好\t123\t44\n');assert.equal(r.text,header+'你好\t123\t44\n');assert.equal(r.st.counts['拼音列不合法'],1);
  });
  await test('default Rime: invalid code and mixed text remain unchanged with report',async()=>{
    const r=await convert(header+'你好\t???\t1\n你 A\tni a\t2\n');
    assert.equal(r.text,header+'你好\t???\t1\n你 A\tni a\t2\n');
    assert.equal(r.st.counts['拼音列不合法'],1);assert.equal(r.st.counts['混合非汉字'],1);
  });
  await test('ambiguous default layout still fails: extra fields and nonnumeric weight',async()=>{
    await assert.rejects(convert(header+'你好\tni hao\t100\textra\n'),/列布局/);
    await assert.rejects(convert(header+'你好\tni hao\tbad-weight\n'),/权重/);
  });
  await test('existing code: preserve apostrophe, numeric tones, polyphone, punctuation and trailing fields',async()=>{
    const a=await convert(header+"重睡\tchóng;xx shuì;yy\t00400\n西安\txi1'An1\t1\n",{toneType:'none'});
    assert.match(a.text,/chóng;/);assert.match(a.text,/shuì;[a-z]{2}\t00400/);assert.match(a.text,/xi1;[a-z]{2}'An1;[a-z]{2}/);
  });
  await test('explicit inline columns: reordered code/text/weight and empty code',async()=>{
    const h='---\ncolumns: [weight, code, text]\n...\n';
    const r=await convert(h+'120\tnǐ hǎo\t你好\n100\t\t你好\n',{mode:'autoOnly'});
    assert.equal(r.text,h+'120\tnǐ hǎo\t你好\n100\tnǐ hǎo\t你好\n');
  });
  await test('explicit block columns missing code: append code; retain weights',async()=>{
    const r=await convert('---\ncolumns:\n  - text\n  - weight\n...\n你好\t123\n',{mode:'autoOnly'});
    assert.equal(r.text,'---\ncolumns:\n  - text\n  - weight\n  - code\n...\n你好\t123\tnǐ hǎo\n');
  });
  await test('explicit columns protections remain: short rows, duplicate/unknown/nested columns, unclosed header',async()=>{
    await assert.rejects(convert('---\ncolumns: [text, code, weight]\n...\n你好\tni hao\n'),/列数/);
    for(const h of ['columns: [text, text]','columns: [text, unknown]','  columns: [text, code]','columns: [text]\ncolumns: [text]'])await assert.rejects(convert('---\n'+h+'\n...\n你好\n'),/columns/);
    await assert.rejects(convert('---\nname: x\n你好\n'),/结束标记/);
  });
  await test('plain TXT/TSV: previous insert-after-word behavior and custom word column unchanged',async()=>{
    assert.equal((await convert('你好\t88\textra\n',{mode:'autoOnly'})).text,'你好\tnǐ hǎo\t88\textra\n');
    assert.equal((await convert('88\t你好\textra\n',{mode:'autoOnly',w:1,p:0})).text,'88\t你好\tnǐ hǎo\textra\n');
  });
  await test('space delimiter with apostrophe-separated code, comments, BOM and no terminal newline',async()=>{
    const r=await convert('\uFEFF---\nname: test\n...\n# keep\n你好 ni\'hao 120',{sep:'space'});
    assert(r.text.startsWith('\uFEFF---\nname: test\n...\n# keep\n'));
    assert.match(r.text,/你好 ni;[a-z]{2}'hao;[a-z]{2} 120$/);
  });
  await test('ZIP deflate/store round trip preserves nested path and non-dictionary payload, CRC checked',async()=>{
    const z=new api.Zip(), entries=[['Rime/sample/mixed-layout.dict.yaml',Buffer.from(yui),8],['Rime/sample/assets.bin',Buffer.from([0,1,2,3,255]),8],['Rime/sample/',Buffer.alloc(0),0]];
    for(const [name,raw,method] of entries){const b=method?deflateRawSync(raw):raw;z.add(name,[b],(api.crcAdd(0xffffffff,raw)^0xffffffff)>>>0,raw.length,b.length,method);}
    const input=z.finish();downloads.length=0;ctx.captureSave=(blob,name)=>downloads.push({blob,name});vm.runInContext('save = captureSave',ctx);
    const file=new Blob([input]);file.name='sample.zip';await api.run([file]);assert.equal(element('msg').className,'msg ok');assert.equal(downloads.length,1);assert.equal(downloads[0].name,'sample.zip');
    const out=await api.readZipEntries(downloads[0].blob);assert.deepEqual(Array.from(out,e=>e.name),entries.map(x=>x[0]));
    const converted=await convert(yui);
    for(let i=0;i<out.length;i++){const bytes=Buffer.from(await new Response(api.checkedStream(out[i])).arrayBuffer());assert(bytes.equals(i===0?Buffer.from(converted.text):entries[i][1]));}
    const e=out[0];await assert.rejects(new Response(api.checkedStream({...e,crc:e.crc^1})).arrayBuffer(),/CRC/);
  });
  console.log('RESULT '+passed+' test groups passed; Node '+process.version+' executing original embedded pinyin/tables/business scripts; not Android WebView runtime.');
})().catch(e=>{console.error(e);process.exitCode=1;});

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
async function test(label, fn) {if(process.env.DICT_TEST_FILTER&&!label.includes(process.env.DICT_TEST_FILTER))return;await fn(); passed++; console.log('PASS '+label);}
function bases(s) {return s.replace(/;[^\s'’]*/g, '');}
// 原 autoHeader 使用 join('\n')，既有的 CRLF 头部会转成 LF；词条正文另验字节。
function lines(s) {return s.split(/\r?\n/);}
function filledFixtureLine(line,index,mode){
  if(index<5||!line.trim()||line.startsWith('#')||line.startsWith('你好 世界\t'))return line;
  const f=line.split('\t');
  if(f.length===1)return mode==='existing'?line+'\t\t120':line+'\txué xí\t120';
  return f.length===2?line+'\t120':line;
}
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
      if(a.length>1) {assert.equal(bases(b[1]),bases(a[1]));assert.equal(b.length,Math.max(a.length,3));assert.equal(b[2],a.length>2?a[2]:'120');}
      else {assert.equal(b.length,3);assert.equal(b[2],'120');assert.match(b[1],/;/);}
      const expectedCodes=Array.from(a[0]).map(c=>api.tables.get('yuanmo').get(c));
      assert.deepEqual(b[1].match(/[^\s'’]+/g).map(t=>t.split(';')[1]),expectedCodes);
    }
    assert.equal(r.st.coded,7);
    assert.equal(r.st.counts['混合非汉字'],1);
    console.log('  fixture processed='+r.st.coded+', counts='+JSON.stringify(r.st.counts));
  });
  await test('public fixture: autoOnly retains existing codes and tones; fills only missing code',async()=>{
    const r=await convert(yui,{mode:'autoOnly'}), a=lines(yui), b=lines(r.text);
    a.forEach((l,i)=>assert.equal(b[i],filledFixtureLine(l,i,'autoOnly')));
    assert.equal(b[missingCodeLine], '学习\txué xí\t120');assert.equal(r.st.coded,1);
    assert.equal(r.text.slice(r.text.indexOf('...')+3),'\r\n'+a.slice(5).map((l,i)=>filledFixtureLine(l,i+5,'autoOnly')).join('\r\n'));
  });
  await test('public fixture: existing mode unchanged; no invented pronunciation for absent code',async()=>{
    const r=await convert(yui,{mode:'existing'});
    assert.equal(r.st.coded,6);assert.equal(lines(r.text)[missingCodeLine],'学习\t\t120');
    for(const [i,line] of lines(r.text).entries())if(i<5||i===mixedTextLine)assert.equal(line,lines(yui)[i]);
    assert.equal(r.st.counts['无 code'],1);assert.equal(r.st.counts['混合非汉字'],1);
  });
  await test('public fixture: replace=false keeps all existing auxiliary codes including multi-code',async()=>{
    const r=await convert(yui,{replace:false}), a=lines(yui),b=lines(r.text);
    a.forEach((l,i)=>assert.equal(b[i],filledFixtureLine(l,i,'autoAux').replace('xué xí',b[missingCodeLine].split('\t')[1])));assert.equal(r.st.coded,1);
  });
  await test('public fixture: all four tables, both tone modes, repeated conversion is idempotent',async()=>{
    for(const [auxType,table] of api.tables)for(const toneType of ['symbol','none']){
      const o={auxType,table,toneType};const a=await convert(yui,o),b=await convert(a.text,o);
      assert.equal(a.text,b.text);assert.equal(a.st.coded,7);
    }
  });
  await test('missing columns: mixed one/two/three fields, empty code, absent/empty/zero weight',async()=>{
    const input=header+'你好\n你好\t123\n你好\tnǐ hǎo\n你好\tnǐ hǎo\t900\n你好\t\t000\n你好\t\n你好\t\t\n';
    const r=await convert(input,{mode:'autoOnly'});
    assert.equal(r.text, header+'你好\tnǐ hǎo\t120\n你好\tnǐ hǎo\t123\n你好\tnǐ hǎo\t120\n你好\tnǐ hǎo\t900\n你好\tnǐ hǎo\t000\n你好\tnǐ hǎo\t120\n你好\tnǐ hǎo\t120\n');
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
    assert.equal((await convert('---\ncolumns: [text, code, weight]\n...\n你好\tni hao\n',{mode:'autoOnly'})).text,'---\ncolumns: [text, code, weight]\n...\n你好\tni hao\t120\n');
    await assert.rejects(convert('---\ncolumns: [text, code, weight, stem]\n...\n你好\tni hao\n'),/列数/);
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
  await test('repeat plain: generated TXT/TSV code is reused with all tables, modes and tone settings',async()=>{
    for(const mode of ['autoOnly','autoAux'])for(const toneType of ['symbol','none'])for(const [auxType,table] of api.tables){
      for(const input of ['你好\n','你好\t88\textra\n','88\t你好\textra\n']){
        const options={mode,toneType,auxType,table,...(input.startsWith('88')?{w:1,p:0}:{})};
        const first=await convert(input,options),second=await convert(first.text,options);
        assert.equal(second.text,first.text,JSON.stringify({input,mode,toneType,auxType}));
      }
    }
  });
  await test('repeat plain: existing pinyin is preserved and auxiliary codes update in place',async()=>{
    const input='你好\tnǐ hǎo\t0088\textra\n重睡\tchóng;xx shuì;yy\t00400\n';
    assert.equal((await convert(input,{mode:'autoOnly'})).text,input);
    const aux=await convert(input),again=await convert(aux.text);
    assert.equal(aux.text,again.text);assert.match(aux.text,/chóng;/);assert.match(aux.text,/\t00400\n$/);
    const rows=aux.text.trimEnd().split('\n').map(x=>x.split('\t'));
    assert.equal(rows[0].length,4);assert.equal(rows[0][2],'0088');assert.equal(rows[0][3],'extra');
    assert.equal(rows[1].length,3);
    const keep=await convert(input,{replace:false});assert.equal(keep.text.split('\n')[1],input.split('\n')[1]);
  });
  await test('repeat plain: custom code column, empty code cell, apostrophe and metadata are kept',async()=>{
    const custom='0088\tnǐ hǎo\t你好\textra\n';
    assert.equal((await convert(custom,{mode:'autoOnly',w:2,p:1})).text,custom);
    assert.equal((await convert('你好\t\t0088\n',{mode:'autoOnly'})).text,'你好\tnǐ hǎo\t0088\n');
    assert.equal((await convert("西安\txī'ān\t12\n",{mode:'autoOnly'})).text,"西安\txī'ān\t12\n");
    assert.equal((await convert('你\tnote\n',{mode:'autoOnly'})).text,'你\tnǐ\t120\tnote\n');
  });
  await test('space round trip: spaced syllables form one code field in plain and Rime inputs',async()=>{
    const inputs=['你好 ni hao 120\n','你好 nǐ hǎo 120 extra\n','你好\n','你好 120 extra\n',header+'你好 nǐ hǎo 120\n'];
    for(const mode of ['autoOnly','autoAux'])for(const toneType of ['symbol','none'])for(const input of inputs){
      const options={mode,toneType,sep:'space'},first=await convert(input,options),again=await convert(first.text,options);
      assert.equal(again.text,first.text,JSON.stringify({input,mode,toneType}));
      assert.equal(first.st.counts['字音数量不匹配']||0,0);
    }
    assert.equal((await convert('你好 nǐ hǎo 00120 extra\n',{mode:'autoOnly',sep:'space'})).text,'你好 nǐ hǎo 00120 extra\n');
    const existing=await convert('你好 nǐ hǎo 00120 extra\n',{mode:'existing',sep:'space'});
    assert.match(existing.text,/^你好 nǐ;[a-z]{2} hǎo;[a-z]{2} 00120 extra\n$/);
    assert.equal((await convert(existing.text,{mode:'existing',sep:'space'})).text,existing.text);
  });
  await test('space round trip: apostrophe compatibility, custom columns, numbers and trailing metadata',async()=>{
    for(const input of ["西安 xi'an 120\n",'绿女 lǜ nǚ 0\n','西安 xi1 an1 120\n']){
      const first=await convert(input,{sep:'space'});assert.equal((await convert(first.text,{sep:'space'})).text,first.text);
    }
    const opt={mode:'autoOnly',sep:'space',w:1,p:2};
    assert.equal((await convert('88 你好 nǐ hǎo extra\n',opt)).text,'88 你好 nǐ hǎo extra\n');
    const reversed={mode:'autoOnly',sep:'space',w:2,p:1};
    assert.equal((await convert('88 nǐ hǎo 你好 extra\n',reversed)).text,'88 nǐ hǎo 你好 extra\n');
    const explicit='---\ncolumns: [weight, code, text]\n...\n120 nǐ hǎo 你好\n';
    assert.equal((await convert(explicit,{mode:'autoOnly',sep:'space'})).text,explicit);
  });
  await test('space round trip: generated Tab fields preserve empty cells, mixed text, BOM and CRLF',async()=>{
    const input='\uFEFF你好\t\t0088\t\textra\r\n你 A\tni a\t12\r\n';
    const first=await convert(input,{mode:'autoOnly',sep:'space'});
    assert.equal(first.text,'\uFEFF你好\tnǐ hǎo\t0088\t\textra\r\n你 A\tni a\t12\r\n');
    assert.equal((await convert(first.text,{mode:'autoOnly',sep:'space'})).text,first.text);
  });
  await test('repeat plain: ambiguous code columns are preserved and reported instead of guessed',async()=>{
    const input='你好\tnǐ hǎo\tnǐ hǎo\n';
    const r=await convert(input,{mode:'autoOnly',p:2});
    assert.equal(r.text,input);assert.equal(r.st.counts['拼音列有歧义，请确认拼音列'],1);assert.equal(r.st.coded,0);
    const weight='88\t你好\t0\n',opt={mode:'autoOnly',w:1,p:0};
    const first=await convert(weight,opt);assert.equal(first.text,'88\t你好\tnǐ hǎo\t0\n');
    assert.equal((await convert(first.text,opt)).text,first.text);
  });
  await test('space round trip: Rime declared code positions and replace=false use grouped syllables',async()=>{
    for(const h of ['---\ncolumns: [text, code, weight]\n...\n','---\n...\n']){
      for(const mode of ['autoOnly','autoAux','existing']){
        const input=h+'你好 nǐ;xx hǎo;yy 00120\n',o={mode,sep:'space',replace:false};
        const first=await convert(input,o);assert.equal(first.text,input);
      }
    }
    const rows='  你好   nǐ    hǎo   00120 extra  \n西安 xī’ān 120\n';
    const r=await convert(rows,{mode:'existing',sep:'space'});
    assert.match(r.text,/^  你好   nǐ;[a-z]{2}    hǎo;[a-z]{2}   00120 extra  \n/);
    assert.equal((await convert(r.text,{mode:'existing',sep:'space'})).text,r.text);
    const expanded='𰻞\n';
    for(const mode of ['autoOnly','autoAux']){
      const first=await convert(expanded,{mode});assert.equal((await convert(first.text,{mode})).text,first.text);
    }
  });
  await test('ZIP repeat plain: space input and generated TSV round trip without duplicate code fields',async()=>{
    element('sep').value='space';element('mode').value='autoOnly';
    try{
      let file=new Blob([]),z=new api.Zip();
      for(const [name,text] of [['dict/a.txt','你好 ni hao 00120\n'],['dict/b.tsv','学习\t99\n']]){
        const bytes=Buffer.from(text);z.add(name,[deflateRawSync(bytes)],(api.crcAdd(0xffffffff,bytes)^0xffffffff)>>>0,bytes.length,deflateRawSync(bytes).length,8);
      }
      file=z.finish();file.name='repeat.zip';
      const previous=[];
      for(let turn=0;turn<2;turn++){
        downloads.length=0;ctx.captureSave=(blob,name)=>downloads.push({blob,name});vm.runInContext('save=captureSave',ctx);
        await api.run([file]);assert.equal(element('msg').className,'msg ok');assert.equal(downloads.length,1);
        const entries=await api.readZipEntries(downloads[0].blob);
        const texts=await Promise.all(Array.from(entries,e=>new Response(api.checkedStream(e)).text()));
        if(turn===0){previous.push(...texts);assert.equal(texts[0],'你好 ni hao 00120\n');assert.equal(texts[1],'学习\txué xí\t99\n');}
        else assert.deepEqual(texts,previous);
        file=downloads[0].blob;file.name='repeat.zip';
      }
    }finally{element('sep').value='tab';element('mode').value='autoAux';}
  });
  await test('1.1.0 interface: four auxiliary tables, renamed tones and concise help',async()=>{
    assert.deepEqual(Array.from(api.tables.keys()).sort(),['moqi','xiaohe','yuanmo','ziranma']);
    assert(!html.includes('id="yhPairs"'));assert(!html.includes('value="original"'));assert(!html.includes('原版音托'));
    assert(html.includes('<label>有声调/无声调<select id="toneType">'));
    assert(html.includes('value="symbol" selected>有声调（默认）'));
    assert(!html.includes('自动刷声调'));assert(!html.includes('声调符号：'));
    const help=html.match(/沿用已识别的拼音列[\s\S]*?<p class="sub">([^<]+)<\/p>/)[1];
    assert(help.length<180);assert(help.includes('缺权重补 120'));
  });
  await test('1.1.0 weights: all modes fill omitted/empty weights with existing pinyin and auxiliary codes',async()=>{
    for(const mode of ['autoOnly','autoAux','existing'])for(const sep of ['tab','space'])for(const replace of [true,false]){
      for(const suffix of ['','\t','\t   ','\t0','\t000','\t0088','\t12.5']){
        const input='你好\tnǐ;xx hǎo;yy'+suffix+'\n';
        const expectedWeight=suffix.trim()?suffix.slice(1):'120';
        for(const prefix of ['',header]){
          const o={mode,sep,replace},r=await convert(prefix+input,o),line=lines(r.text).filter(x=>x.startsWith('你好'))[0];
          const fields=line.split('\t');assert.equal(fields.length,3);assert.equal(fields[2],expectedWeight);
          assert.equal(bases(fields[1]),'nǐ hǎo');
          if(mode==='autoOnly'||!replace)assert.equal(fields[1],'nǐ;xx hǎo;yy');
          assert.equal((await convert(r.text,o)).text,r.text);
        }
      }
    }
  });
  await test('1.1.0 weights: missing weight without existing code, whitespace input and trailing metadata',async()=>{
    for(const mode of ['autoOnly','autoAux','existing']){
      const r=await convert('你好\n',{mode});const f=lines(r.text)[0].split('\t');
      assert.equal(f.length,3);assert.equal(f[0],'你好');assert.equal(f[2],'120');
      assert.equal(mode==='existing'?f[1]:bases(f[1]),mode==='existing'?'':'nǐ hǎo');
      const withMeta=await convert('你好\tnǐ hǎo\tremark\n',{mode});
      assert.deepEqual(lines(withMeta.text)[0].split('\t').slice(2),['120','remark']);
      const spaced=await convert('  你好   nǐ;xx hǎo;yy   \n',{mode,replace:false,sep:'space'});
      assert.equal(spaced.text,'  你好   nǐ;xx hǎo;yy 120   \n');
      assert.equal((await convert(spaced.text,{mode,replace:false,sep:'space'})).text,spaced.text);
    }
  });
  await test('1.1.0 weights: declared order, missing declaration, stem and omitted trailing weight',async()=>{
    const cases=[
      ['[text, code]','你好\tnǐ;xx hǎo;yy','[text, code, weight]','你好\tnǐ;xx hǎo;yy\t120'],
      ['[code, text, stem]','nǐ;xx hǎo;yy\t你好\tmeta','[code, text, stem, weight]','nǐ;xx hǎo;yy\t你好\tmeta\t120'],
      ['[weight, text, code]','\t你好\tnǐ;xx hǎo;yy','[weight, text, code]','120\t你好\tnǐ;xx hǎo;yy'],
      ['[text, code, weight]','你好\tnǐ;xx hǎo;yy','[text, code, weight]','你好\tnǐ;xx hǎo;yy\t120'],
      ['[text, code, weight, stem]','你好\tnǐ;xx hǎo;yy\t\tmeta','[text, code, weight, stem]','你好\tnǐ;xx hǎo;yy\t120\tmeta'],
      ['[weight, text, code, stem]','000\t你好\tnǐ;xx hǎo;yy\tmeta','[weight, text, code, stem]','000\t你好\tnǐ;xx hǎo;yy\tmeta']
    ];
    for(const mode of ['autoOnly','autoAux','existing'])for(const [columns,input,outColumns,expected] of cases){
      const h='---\ncolumns: '+columns+'\n...\n',o={mode,replace:false};
      const r=await convert(h+input+'\n',o);
      assert.equal(r.text,'---\ncolumns: '+outColumns+'\n...\n'+expected+'\n');
      assert.equal((await convert(r.text,o)).text,r.text);
    }
    for(const mode of ['autoOnly','autoAux','existing']){
      const r=await convert('---\ncolumns:\n  - text\n...\n你好\n',{mode});
      assert(r.text.startsWith('---\ncolumns:\n  - text\n  - code\n  - weight\n...\n'));
      const f=lines(r.text).filter(l=>l.startsWith('你好'))[0].split('\t');assert.equal(f.length,3);assert.equal(f[2],'120');
      assert.equal((await convert(r.text,{mode})).text,r.text);
    }
  });
  await test('1.1.0 weights: reported rows stay visible, known missing table still fills and ambiguous rows stay intact',async()=>{
    const input=header+'你好\tnǐ;xx hǎo;yy\n';
    const r=await convert(input,{table:new Map()});
    assert.equal(r.text,input.replace('hǎo;yy\n','hǎo;yy\t120\n'));assert.equal(r.st.counts['缺辅助码'],1);
    const mixed='你好 世界\tni hao shi jie\n';assert.equal((await convert(mixed)).text,mixed);
    const ambiguous='你好\tni hao\tni hao\n';assert.equal((await convert(ambiguous,{p:2})).text,ambiguous);
  });
  await test('1.1.0 weights: failed generation preserves Rime numeric weight outside the code column',async()=>{
    for(const sep of ['tab','space'])for(const suffix of ['', '00123','0','000','12.5']){
      const line=sep==='tab'?'你好'+(suffix?'\t'+suffix:''):'你好'+(suffix?' '+suffix:'');
      const r=await convert(header+line+'\n',{mode:'autoAux',sep,table:new Map()});
      assert.equal(r.text,header+'你好\t\t'+(suffix||'120')+'\n');
      assert.equal(r.st.counts['缺辅助码'],1);
      const again=await convert(r.text,{mode:'autoAux',sep,table:new Map()});assert.equal(again.text,r.text);
    }
  });
  await test('1.1.0 weights: plain missing-code rows retain weights and metadata across all modes',async()=>{
    for(const mode of ['autoOnly','autoAux','existing']){
      const options={mode,...(mode==='autoAux'?{table:new Map()}:{})};
      for(const [input,weight,meta] of [['你好\t00123\n','00123',null],['你好\tremark\n','120','remark'],['你好\n','120',null]]){
        const r=await convert(input,options),fields=lines(r.text)[0].split('\t');
        assert.equal(fields[0],'你好');assert.equal(fields[2],weight);
        if(mode==='autoOnly')assert.equal(fields[1],'nǐ hǎo');else assert.equal(fields[1],'');
        if(meta)assert.equal(fields[3],meta);
        assert.equal((await convert(r.text,options)).text,r.text);
      }
    }
  });
  await test('1.1.0 weights: BOM and CRLF are retained while appending weight',async()=>{
    for(const mode of ['autoOnly','autoAux','existing']){
      const r=await convert('\uFEFF你好\tnǐ;xx hǎo;yy\r\n',{mode,replace:false});
      assert.equal(r.text,'\uFEFF你好\tnǐ;xx hǎo;yy\t120\r\n');
    }
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

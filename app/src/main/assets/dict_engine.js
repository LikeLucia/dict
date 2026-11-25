if (typeof String.prototype.matchAll !== 'function') {
  String.prototype.matchAll = function (regex) {
    var source = String(this), out = [], match;
    if (!regex.global) throw new TypeError('matchAll requires a global regexp');
    regex.lastIndex = 0;
    while ((match = regex.exec(source)) !== null) {
      out.push(match);
      if (match[0] === '') regex.lastIndex++;
    }
    return out;
  };
}
if (typeof String.prototype.normalize !== 'function') {
  var __toneMap = {'ā':'a','á':'a','ǎ':'a','à':'a','ē':'e','é':'e','ě':'e','è':'e','ī':'i','í':'i','ǐ':'i','ì':'i','ō':'o','ó':'o','ǒ':'o','ò':'o','ū':'u','ú':'u','ǔ':'u','ù':'u','ǖ':'ü','ǘ':'ü','ǚ':'ü','ǜ':'ü','ń':'n','ň':'n','ǹ':'n','ḿ':'m'};
  String.prototype.normalize = function (form) {
    return String(this).replace(/[āáǎàēéěèīíǐìōóǒòūúǔùǖǘǚǜńňǹḿ]/g, function (c) { return __toneMap[c] || c; });
  };
}

var CRC = (function(){
  var t = new Uint32Array(256);
  for(var i = 0; i < 256; i++){
    var c = i;
    for(var k = 0; k < 8; k++) c = (c & 1) ? (0xEDB88320 ^ (c >>> 1)) : (c >>> 1);
    t[i] = c >>> 0;
  }
  return t;
})();
function crcAdd(crc, bytes){
  for(var i = 0; i < bytes.length; i++) crc = CRC[(crc ^ bytes[i]) & 0xFF] ^ (crc >>> 8);
  return crc >>> 0;
}
var canDef = null;
async function deflateOK(){
  if(canDef !== null) return canDef;
  if(typeof CompressionStream === 'undefined'){ canDef = false; return canDef; }
  try{
    var cs = new CompressionStream('deflate-raw');
    var w = cs.writable.getWriter(), r = cs.readable.getReader();
    var done = (async function(){ await w.write(new Uint8Array([7,8,9])); await w.close(); })();
    while(true){ var o = await r.read(); if(o.done) break; }
    await done; canDef = true;
  }catch(e){ canDef = false; }
  return canDef;
}
function Zip(){
  this.parts = []; this.dir = []; this.off = 0; this.n = 0;
  var d = new Date();
  this.t = ((d.getHours() << 11) | (d.getMinutes() << 5) | (d.getSeconds() >> 1)) & 0xFFFF;
  this.d = (((d.getFullYear() - 1980) << 9) | ((d.getMonth() + 1) << 5) | d.getDate()) & 0xFFFF;
}
Zip.prototype.add = function(name, parts, crc, raw, comp, method){
  if(this.n>=65534 || this.off+comp+name.length*4+100>0xffffffff || raw>0xffffffff || comp>0xffffffff) throw Error('输出超过经典 ZIP 容量');
  var nb = new TextEncoder().encode(name); if(nb.length>65535) throw Error('ZIP 文件名过长');
  var lh = new Uint8Array(30 + nb.length), dv = new DataView(lh.buffer);
  dv.setUint32(0, 0x04034b50, true); dv.setUint16(4, 20, true); dv.setUint16(6, 0x0808, true);
  dv.setUint16(8, method, true); dv.setUint16(10, this.t, true); dv.setUint16(12, this.d, true);
  dv.setUint32(14, 0, true); dv.setUint32(18, 0, true); dv.setUint32(22, 0, true);
  dv.setUint16(26, nb.length, true); dv.setUint16(28, 0, true);
  lh.set(nb, 30);
  var start = this.off;
  this.parts.push(lh); this.off += lh.length;
  for(var i = 0; i < parts.length; i++){ this.parts.push(parts[i]); this.off += parts[i].length; }
  var dd = new Uint8Array(16), dv2 = new DataView(dd.buffer);
  dv2.setUint32(0, 0x08074b50, true); dv2.setUint32(4, crc, true);
  dv2.setUint32(8, comp, true); dv2.setUint32(12, raw, true);
  this.parts.push(dd); this.off += dd.length;
  var cd = new Uint8Array(46 + nb.length), cv = new DataView(cd.buffer);
  cv.setUint32(0, 0x02014b50, true); cv.setUint16(4, 20, true); cv.setUint16(6, 20, true);
  cv.setUint16(8, 0x0808, true); cv.setUint16(10, method, true); cv.setUint16(12, this.t, true);
  cv.setUint16(14, this.d, true); cv.setUint32(16, crc, true); cv.setUint32(20, comp, true);
  cv.setUint32(24, raw, true); cv.setUint16(28, nb.length, true); cv.setUint16(30, 0, true);
  cv.setUint16(32, 0, true); cv.setUint16(34, 0, true); cv.setUint16(36, 0, true);
  cv.setUint32(38, 0, true); cv.setUint32(42, start, true);
  cd.set(nb, 46);
  this.dir.push(cd); this.n++;
};
Zip.prototype.finish = function(){
  var dirSize = 0;
  for(var i = 0; i < this.dir.length; i++) dirSize += this.dir[i].length;
  var dirOff = this.off;
  for(var j = 0; j < this.dir.length; j++) this.parts.push(this.dir[j]);
  var end = new Uint8Array(22), ed = new DataView(end.buffer);
  ed.setUint32(0, 0x06054b50, true); ed.setUint16(4, 0, true); ed.setUint16(6, 0, true);
  ed.setUint16(8, this.n, true); ed.setUint16(10, this.n, true);
  ed.setUint32(12, dirSize, true); ed.setUint32(16, dirOff, true); ed.setUint16(20, 0, true);
  this.parts.push(end);
  var blob = new Blob(this.parts, { type: 'application/zip' });
  this.parts = []; this.dir = [];
  return blob;
};


'use strict';
const $=id=>document.getElementById(id);let tables=new Map(),busy=false;
const supported=n=>/\.(yaml|yml|txt)$/i.test(n);
function mapOf(obj){return new Map(Object.entries(obj));}
async function init(){const raw=Uint8Array.from(atob($('auxTables').textContent.trim()),c=>c.charCodeAt(0));const text=await new Response(new Blob([raw]).stream().pipeThrough(new DecompressionStream('deflate-raw'))).text();const data=JSON.parse(text);for(const [id,obj] of Object.entries(data))tables.set(id,mapOf(obj));}
const ready=Promise.resolve();
function getOpt(){const w=$('wordCol').value,p=$('pyCol').value,s=$('auxSep').value;if(!/^[1-9]\d*$/.test(w)||!/^[1-9]\d*$/.test(p)||!Number.isSafeInteger(+w)||!Number.isSafeInteger(+p)||w===p)throw Error('列号须为不同的正整数');if(!s||/[\s'’a-zA-Z0-9ü\u0300-\u036f]/u.test(s))throw Error('辅助码分隔符请使用非字母数字、非空白、非撇号符号');const auxType=$('auxType').value;return{mode:$('mode').value,toneType:$('toneType').value,w:+w-1,p:+p-1,sep:$('sep').value,encoding:$('enc').value,replace:$('replace').checked,aux:s,auxType,table:tables.get(auxType)}}
function state(opt){return{opt:{...opt},phase:'start',header:[],no:0,coded:0,counts:{},samples:[],missing:new Set(),polyphonic:0,polyphonicSamples:[],polyphonicDetails:[],auxStripped:0}}
function note(st,why,raw){st.counts[why]=(st.counts[why]||0)+1;if(st.samples.length<60)st.samples.push('第 '+st.no+' 行 '+why+'：'+raw.slice(0,180));return raw}
function headerColumns(st){const h=st.header;let found=false,a=null;
 for(let i=0;i<h.length;i++){const m=h[i].match(/^columns\s*:\s*(.*?)\s*$/);if(!m)continue;if(found)throw Error('重复 columns 声明');found=true;let v=m[1].replace(/\s+#.*$/,'').trim();if(v.startsWith('[')&&v.endsWith(']'))a=v.slice(1,-1).split(',').map(x=>x.trim());else if(!v){a=[];let j=i+1;for(;j<h.length;j++){if(/^\s*(#.*)?$/.test(h[j]))continue;let b=h[j].match(/^\s*-\s+([^#]*?)(?:\s+#.*)?$/);if(b){a.push(b[1].trim());continue}break}}else throw Error('未知 columns 声明');a=a.map(x=>x.replace(/^(['"])(.*?)\1$/,'$2'));if(a.some(x=>!/^[A-Za-z_][A-Za-z0-9_.-]*$/.test(x))||new Set(a).size!==a.length)throw Error('无效或重复 columns：'+a.join(', '));if(!a.includes('text')||!a.includes('code'))throw Error('columns 缺少 text 或 code');st.opt.w=a.indexOf('text');st.opt.p=a.indexOf('code');}
}
// 只做基本拼音形态校验，不声称识别所有双拼或形码。
// Bound pure validation caches; raw syllable keys preserve the existing Unicode rules.
var __validSyllableCache=new Map(),__strictSyllableCache=new Map();
const __SYLLABLE_CACHE_LIMIT=4096;
function validSyllable(s){
 const cached=__validSyllableCache.get(s);if(cached!==undefined)return cached;
 let n=s.normalize('NFD').replace(/[̄́̌̀]/g,'').normalize('NFC').toLowerCase(),result=false;
 if(/^[a-züvê]+[0-5]?$/.test(n)){n=n.replace(/[0-5]$/,'');result=/[aeiouüvê]/.test(n)||/^(m|n|ng|hm|hng)$/.test(n)}
 if(__validSyllableCache.size<__SYLLABLE_CACHE_LIMIT)__validSyllableCache.set(s,result);
 return result;
}
function __matchesStandardSyllable(base){
 const cached=__strictSyllableCache.get(base);if(cached!==undefined)return cached;
 let result=false;
 if(validSyllable(base)){
  const n=base.normalize('NFD').replace(/[̄́̌̀]/g,'').normalize('NFC').toLowerCase().replace(/[0-5]$/,'').replace(/ü/g,'v');
  result=/^(?:(?:zh|ch|sh|[bpmfdtnlgkhjqxzcsrwy])?(?:a|o|e|ê|ai|ei|ao|ou|an|en|ang|eng|ong|i|ia|ie|iao|iu|ian|in|iang|ing|iong|u|ua|uo|ue|uai|ui|uan|un|uang|ueng|v|ve|van|vn|er)|m|n|ng|hm|hng)$/.test(n);
 }
 if(__strictSyllableCache.size<__SYLLABLE_CACHE_LIMIT)__strictSyllableCache.set(base,result);
 return result;
}
// 辅助码允许一字多码（如 momo / kirakira 的兼容码）：逗号分隔的多个两字母码。
function validAux(code){return typeof code==='string'&&/^[a-z]{2}(?:,[a-z]{2})*$/i.test(code)}
// 自动识别只接受完整音节，避免把 extra、note 等备注当作拼音列。
function matchesCode(code,word,opt){
 const chars=Array.from(word),tokens=code.trim().split(/[\s'’]+/).filter(Boolean);
 if(!chars.length||!chars.every(c=>isHan(c))||tokens.length!==chars.length)return false;
 return tokens.every(token=>__matchesStandardSyllable(token.split(opt.aux)[0]));
}
function parseRow(raw,opt,w,codeColumns){
 let spans=[];
 // 生成文件始终用 Tab 分列；在空白模式中也优先保留这些明确边界与空字段。
 if((opt.inputSep||'tab')==='tab'||raw.includes('\t')){
  let start=0;for(let i=0;i<=raw.length;i++)if(i===raw.length||raw[i]==='\t'){spans.push([start,i]);start=i+1}
 }else{
  for(const m of raw.matchAll(/\S+/g))spans.push([m.index,m.index+m[0].length]);
  const candidates=new Map(),field=i=>spans[i]?raw.slice(...spans[i]).replace(/^\uFEFF/,''):'';
  function add(p,end,word){
   if(end>spans.length||!spans[p]||!spans[end-1]||!matchesCode(raw.slice(spans[p][0],spans[end-1][1]),word,opt))return;
   const grouped=[...spans.slice(0,p),[spans[p][0],spans[end-1][1]],...spans.slice(end)];candidates.set(JSON.stringify(grouped),grouped);
  }
  for(const p of new Set(codeColumns)){
   if(p===w||p<0||p>=spans.length)continue;
   if(p>w){
    const word=field(w),count=Array.from(word).length;let n=0;
    for(let end=p;end<spans.length;end++){
     n+=field(end).split(/[\s'’]+/).filter(Boolean).length;
     if(n===count){add(p,end+1,word);break}if(n>count)break;
    }
   }else{
    // code 在 text 前时，音节合并后才得到逻辑词语列。
    for(let actual=w;actual<spans.length;actual++)add(p,p+actual-w+1,field(actual));
   }
  }
  if(candidates.size>1)return{ambiguous:true};
  if(candidates.size===1)spans=candidates.values().next().value;
 }
 return{spans,fields:spans.map(([a,b])=>raw.slice(a,b))};
}
// Internal Tab columns are independent of the final output format.
function outSep(st,raw=''){return String.fromCharCode(9)}
function plainLayout(fields,opt){
 const word=(fields[opt.w]||'').replace(/^\uFEFF/,'');
 if(!word||opt.w<0||opt.w>=fields.length||opt.p===opt.w)return null;
 const p=opt.p;if(!Number.isInteger(p)||p<0)return null;
 const existing=p<fields.length?(fields[p]||''):'';
 // 用户显式指定的拼音列优先。若该列已有非拼音内容（如权重），在它前面插入空列，不能覆盖原值。
 if(existing.trim()!==''&&!matchesCode(existing,word,opt))return{input:fields,w:opt.w,p,has:true,insertAt:true};
 return{input:fields,w:opt.w,p,has:true,insertAt:false};
}
function legacyConvertLine(raw,st,parsed=null){st.no++;const t=raw.replace(/^\uFEFF/,'').trim();if(st.phase==='start'){if(!t||t.startsWith('#'))return raw;if(t==='---'||/^%YAML\b/.test(t)){st.phase='header';return raw}st.phase='body'}if(st.phase==='header'){if(/^\.\.\.(?:\s+#.*)?$/.test(t)){headerColumns(st);st.phase='body';st.header=[]}else{st.header.push(raw);if(st.header.length>100000)throw Error('header 超过 100000 行且未闭合')}return raw}if(!t||t.startsWith('#'))return raw;
 const o=st.opt,row=parsed||parseRow(raw,o,o.w,[o.p]);if(row.ambiguous)return note(st,'拼音列有歧义，请改用 Tab 分列',raw);const spans=row.spans;
 const field=i=>spans[i]?raw.slice(...spans[i]):'';const word=field(o.w).replace(/^\uFEFF/,'');if(!word)return note(st,'缺少词语列',raw);if(!Array.from(word).every(c=>isHan(c)))return note(st,'混合非汉字',raw);const py=field(o.p);if(!py.trim())return note(st,'无 code',raw);
 const tokens=py.match(/[^\s'’]+/g)||[],bases=tokens.map(x=>x.split(o.aux)[0]);if(!tokens.length||bases.some(x=>!validSyllable(x)))return note(st,'拼音列不合法',raw);const chars=Array.from(word);if(chars.length!==tokens.length)return note(st,'字音数量不匹配',raw);let missing=chars.filter(c=>!st.opt.table.has(c)||!validAux(st.opt.table.get(c)));if(missing.length){missing.forEach(c=>st.missing.add(c));return note(st,'缺辅助码',raw)}if(!o.replace&&tokens.some(x=>x.includes(o.aux)))return note(st,'已有辅助码',raw);let i=0;const out=py.replace(/[^\s'’]+/g,()=>bases[i]+o.aux+st.opt.table.get(chars[i++]));st.coded++;st.auxAdded=(st.auxAdded||0)+1;const [a,b]=spans[o.p];return raw.slice(0,a)+out+raw.slice(b)
}
function stripAuxFromRow(raw,st,row,p){
 const spans=row.spans;if(!spans||!spans[p])return raw;
 const [a,b]=spans[p];const py=raw.slice(a,b);
 if(!py.trim())return raw;
 const out=py.replace(/[^\s'’]+/g,token=>token.split(st.opt.aux)[0]);
 if(out===py)return raw;
 st.auxStripped=(st.auxStripped||0)+1;
 return raw.slice(0,a)+out+raw.slice(b);
}
function autoMode(st){return st.opt.mode==='autoAux'||st.opt.mode==='autoOnly'}
function autoHeader(st){
 const h=st.autoLines.map(x=>x.raw),hits=[];
 for(let i=0;i<h.length;i++)if(/^columns\s*:/.test(h[i]))hits.push(i);
 if(h.some(x=>/^\s+columns\s*:/.test(x)))throw Error('不支持嵌套 columns 声明');
 if(hits.length>1)throw Error('重复 columns 声明');
 if(!hits.length){st.layout={standard:true}}
 else{
  const at=hits[0];let val=h[at].replace(/^columns\s*:\s*/,'').replace(/\s+#.*$/,'').trim(),a=[],end=at+1,block=false;
  if(/^\[.*\]$/.test(val))a=val.slice(1,-1).split(',').map(x=>x.trim());
  else if(!val){block=true;for(;end<h.length;end++){if(/^\s*(#.*)?$/.test(h[end]))continue;let m=h[end].match(/^\s*-\s+([^#]*?)(?:\s+#.*)?$/);if(!m)break;a.push(m[1].trim())}}
  else throw Error('复杂 columns 声明不支持');
  a=a.map(x=>x.replace(/^(['"])(.*?)\1$/,'$2'));
  if(!a.length||a.some(x=>!/^[A-Za-z_][A-Za-z0-9_.-]*$/.test(x))||new Set(a).size!==a.length||!a.includes('text'))throw Error('columns 含无效、重复列或缺少 text');
  const input=a.slice(),has=a.includes('code');
  if(!has)a.push('code');if(!a.includes('weight'))a.push('weight');
  st.layout={input,output:a,w:input.indexOf('text'),p:has?input.indexOf('code'):input.length,has};
  const added=a.slice(input.length);
  if(added.length){
   if(block){const indent=h.slice(at+1,end).find(x=>/^\s*-/.test(x))?.match(/^\s*/)[0]||'';h.splice(end,0,...added.map(x=>indent+'- '+x))}
   else h[at]='columns: ['+a.join(', ')+']';
  }
 }
 const fallback=st.autoLines.find(x=>x.delimiter)?.delimiter||'\n',delimiters=new Map();
 for(const row of st.autoLines){const q=delimiters.get(row.raw)||[];q.push(row.delimiter||fallback);delimiters.set(row.raw,q)}
 st.autoLines=h.map(raw=>{const q=delimiters.get(raw);return{raw,delimiter:q&&q.length?q.shift():fallback}});
 return h;
}
function defaultRimeLayout(fields,no,opt){
 const isWeight=v=>/^\d+(?:\.\d+)?$/.test(v);
 // 未声明 columns 时默认 text/code/weight；用户显式改了词语列/拼音列时按显式列处理，
 // 仍保留原有权重列，不能把权重当成拼音覆盖。
 if(opt&&(opt.w!==0||opt.p!==1)){
  const w=opt.w,p=opt.p;
  if(Number.isInteger(w)&&w>=0&&w<fields.length&&Number.isInteger(p)&&p>=0&&p!==w){
   const word=(fields[w]||'').replace(/^\uFEFF/,'');
   if(word){
    const existing=p<fields.length?(fields[p]||''):'';
    const insertAt=existing.trim()!==''&&!matchesCode(existing,word,opt);
    return{input:fields,w,p,has:true,insertAt};
   }
  }
 }
 if(fields.length>3){
  const word=(fields[0]||'').replace(/^\uFEFF/,''),code=(fields[1]||'').trim();
  const codeOK=code===''||matchesCode(fields[1]||'',word,opt);
  // 权重允许非数字占位（如部分 pro 词库的 6N/45N），原样保留，不能因此中断整本词库。
  if(codeOK)return{standard:true,w:0,p:1,has:true,preserveExtra:true};
  // Bad code is diagnosed per row below, irrespective of trailing columns.
  return{input:fields,w:0,p:1,has:true,preserveExtra:true};
 }
 if(fields.length===1||(fields.length===2&&isWeight(fields[1])))return{standard:true,w:0,weightAt:fields.length===2?1:null};
 // 默认第三列作为 weight；非数字占位保留原值，不阻断处理。
 return{input:fields,w:0,p:1,has:true};
}
var __ziWeights={},__ziMetadata=new Map();
function __setPinyinWeights(json){__ziWeights=JSON.parse(json);__ziMetadata.clear();}
function __ziInfo(ch){
 var cached=__ziMetadata.get(ch);if(cached!==undefined)return cached;
 var m=__ziWeights[ch],poly=!!m&&Object.keys(m).length>=2,best='',bestWeight=-1;
 if(m)for(var k in m){var w=Number(m[k])||0;if(w>bestWeight){bestWeight=w;best=k}}
 cached={poly:poly,best:best};
 if(__ziMetadata.size<8192)__ziMetadata.set(ch,cached);
 return cached;
}
function isPolyphonicChar(ch){return __ziInfo(ch).poly;}
function bestZiReading(ch){return __ziInfo(ch).best;}
function applyZiReadings(word,syllables,toneType){
 const chars=Array.from(word);if(chars.length!==syllables.length)return syllables;
 return syllables.map(function(s,i){
  const ch=chars[i];if(!isPolyphonicChar(ch))return s;
  const best=bestZiReading(ch);if(!best)return s;
  return toneType==='none'?pinyinPro.convert(best,{format:'toneNone'}):best;
 });
}
function generateSyllables(word,st){
 const toneType=st.opt.toneType||'symbol';const po={type:'array',toneType};if(toneType==='none')po.v=true;
 let syll=pinyinPro.pinyin(word,po).map(x=>toneType==='num'?x.toLowerCase().replace(/ü/g,'v'):x.toLowerCase());
 if(syll.length!==Array.from(word).length||syll.some(x=>!validSyllable(x)))return null;
 return applyZiReadings(word,syll,toneType);
}
function replacePinyinInRow(raw,st,row,w,p,addAux,replaceAux){
 const o=st.opt,spans=row.spans;if(!spans||!spans[p])return raw;
 const word=(row.fields[w]||'').replace(/^\uFEFF/,'');
 const chars=Array.from(word),syll=generateSyllables(word,st);
 if(!syll)return note(st,'未识别读音',raw);
 if(addAux){
  const missing=chars.filter(c=>!o.table.has(c)||!validAux(o.table.get(c)));
  if(missing.length){missing.forEach(c=>st.missing.add(c));return note(st,'缺辅助码',raw)}
 }
 const [a,b]=spans[p],py=raw.slice(a,b),tokens=py.match(/[^\s'’]+/g)||[];
 if(tokens.length!==chars.length)return note(st,'字音数量不匹配',raw);
 if(tokens.some(x=>!validSyllable(x.split(o.aux)[0])))return note(st,'拼音列不合法',raw);
 let i=0;
 const out=py.replace(/[^\s'’]+/g,token=>{
  const base=token.split(o.aux)[0],suffix=token.slice(base.length);
  let tail=suffix;
  if(suffix){if(replaceAux)tail=o.aux+o.table.get(chars[i]);}
  else if(addAux)tail=o.aux+o.table.get(chars[i]);
  return syll[i++]+tail;
 });
 st.coded++;st.pyReplaced=(st.pyReplaced||0)+1;st.pyAdded=(st.pyAdded||0)+1;if(addAux)st.auxAdded=(st.auxAdded||0)+1;
 return raw.slice(0,a)+out+raw.slice(b);
}
function canonicalReading(reading){
 const symbol=pinyinPro.convert(String(reading).toLowerCase().replace(/v/g,'ü'),{format:'numToSymbol'});
 return 'ā'.normalize('NFC')==='ā'?symbol.normalize('NFC'):symbol;
}
function notePolyphonic(st,word,output,layout,generated){
 const chars=Array.from(word);if(chars.length<2)return;
 const hits=chars.filter(isPolyphonicChar);if(!hits.length)return;
 st.polyphonic=(st.polyphonic||0)+1;
 const unique=[...new Set(hits)];
 st.polyphonicSamples.push('第 '+st.no+' 行 多音字：'+word+'（'+unique.join('、')+'）');
 const p=layout.p==null?layout.w+1:layout.p,row=parseRow(output,st.opt,layout.w,[p]);
 const code=!row.ambiguous?(row.fields[p]||''):'',tokens=code.trim().split(/[\s'’]+/).filter(Boolean).map(x=>x.split(st.opt.aux)[0]);
 const valid=tokens.length===chars.length&&tokens.every(validSyllable),readings=[];
 for(let i=0;i<chars.length;i++){
  const ch=chars[i];if(!isPolyphonicChar(ch))continue;
  const used=valid?tokens[i]:'',all=Object.keys(__ziWeights[ch]||{}),symbol=canonicalReading(used);
  let excluded=symbol;
  // Generated toneless output still has a known weighted choice; preserved toneless code does not.
  if(generated&&used){const best=bestZiReading(ch);if(pinyinPro.convert(best,{format:'toneNone'}).replace(/ü/g,'v')===used.toLowerCase().replace(/ü/g,'v'))excluded=canonicalReading(best)}
  const others=all.filter(x=>canonicalReading(x)!==excluded);
  readings.push({character:ch,index:i,used:used,others:others,toneUnspecified:!!used&&!generated&&!all.some(x=>canonicalReading(x)===symbol)});
 }
 st.polyphonicDetails.push({line:st.no,word:word,characters:unique,pinyin:valid?tokens.join(' '):'',readings:readings});
}
const DEFAULT_WEIGHT='120';
function padDeclaredFields(fields,layout,no){
 // Keep undeclared trailing columns (for example Rime comments) verbatim.
 const missing=layout.input.slice(fields.length);
 // columns 可以省略 code（常见于只写 text/weight/stem 的词库）；补空 code，保留已有权重。
 // Missing declared trailing columns are retained as empty values.
 while(fields.length<layout.input.length)fields.push('');
}
function fillRowWeight(raw,st,layout,codeAdded=false){
 // An existing numeric third column needs no weight edit, even when earlier checks would reject the row.
 if(typeof raw==='string'&&layout&&layout.has&&!layout.output&&!layout.rimeDefault&&layout.w===0&&layout.p===1){
  const first=raw.indexOf('\t'),second=first<0?-1:raw.indexOf('\t',first+1);
  if(second>=0&&raw.indexOf('\t',second+1)<0&&/^\d+(?:\.\d+)?$/.test(raw.slice(second+1)))return raw;
 }
 let row=parseRow(raw,st.opt,layout.w,[layout.p]);if(row.ambiguous)return raw;
 const word=(row.fields[layout.w]||'').replace(/^\uFEFF/,'');
 if(!Array.from(word).length||!Array.from(word).every(c=>isHan(c)))return raw;
 let weight,insert=false;
 if(layout.output){
  if(!layout.has&&!codeAdded){
   const fields=row.fields.slice();while(fields.length<layout.input.length)fields.push('');fields.splice(layout.p,0,'');
   if(fields.length)fields[0]=fields[0].replace(/^\uFEFF/,'');
   raw=(raw.startsWith('\uFEFF')?'\uFEFF':'')+fields.join(outSep(st,raw));row=parseRow(raw,st.opt,layout.w,[layout.p]);
  }
  weight=layout.output.indexOf('weight');
 }else if(layout.rimeDefault){
  if(layout.standard&&!codeAdded){
   const out=row.fields.slice();
   if(out.length)out[0]=word;
   if(out.length<2)out.push('');
   // Two-field Rime rows are text/weight; insert the omitted code column.
   if(out.length===2&&/^\d+(?:\.\d+)?$/.test((out[1]||'').trim()))out.splice(1,0,'');
   raw=(raw.startsWith('\uFEFF')?'\uFEFF':'')+out.join(outSep(st,raw));row=parseRow(raw,st.opt,0,[1]);
  }
  weight=2;
 }else{
  if(!layout.has&&!codeAdded){
   const out=row.fields.slice();out.splice(layout.p,0,'');
   if(out.length)out[0]=out[0].replace(/^\uFEFF/,'');
   raw=(raw.startsWith('\uFEFF')?'\uFEFF':'')+out.join(outSep(st,raw));row=parseRow(raw,st.opt,layout.w,[layout.p]);
  }
  if((row.fields[layout.p]||'').trim()&&!matchesCode(row.fields[layout.p],word,st.opt))return raw;
  // 纯文本没有 weight 声明：保留现存数字权重，否则在 text/code 后补列。
  if(row.fields.some((v,i)=>i!==layout.w&&i!==layout.p&&/^\d+(?:\.\d+)?$/.test(v)))return raw;
  weight=Math.max(layout.w,layout.p)+1;
  insert=!!row.spans[weight]&&row.fields[weight].trim()!=='';
 }
 const current=row.spans[weight],fillWeight=st.opt.weight||DEFAULT_WEIGHT,sep=outSep(st,raw);
 if(current){
  if(insert){st.weights=(st.weights||0)+1;return raw.slice(0,current[0])+fillWeight+sep+raw.slice(current[0]);}
  if(row.fields[weight].trim()!=='')return raw;
  st.weights=(st.weights||0)+1;return raw.slice(0,current[0])+fillWeight+raw.slice(current[1]);
 }
 st.weights=(st.weights||0)+1;
 if(weight===row.fields.length){const end=row.spans.length?row.spans[row.spans.length-1][1]:raw.length;return raw.slice(0,end)+sep+fillWeight+raw.slice(end);}
 const out=row.fields.slice();while(out.length<weight)out.push('');out.push(fillWeight);
 if(out.length)out[0]=out[0].replace(/^\uFEFF/,'');
 return (raw.startsWith('\uFEFF')?'\uFEFF':'')+out.join(outSep(st,raw));
}
function autoConvert(raw,st,delimiter=''){
 const t=raw.replace(/^\uFEFF/,'').trim();st.no++;
 if(st.autoPhase==='header'||(!st.autoPhase&&(t==='---'||t.startsWith('%YAML')))){
  st.headerBytes=(st.headerBytes||0)+(raw.length+delimiter.length)*2;
  if(st.headerBytes>2*1024*1024)throw Error('Rime header 超过 2 MiB UTF-16 预算');
 }
 if(!st.autoPhase){if(!t||t.startsWith('#'))return raw;if(t==='---'||/^%YAML\b/.test(t)){st.autoPhase='header';st.autoLines=[{raw,delimiter}];return null}st.autoPhase='body'}
 if(st.autoPhase==='header'){st.autoLines.push({raw,delimiter});if(st.autoLines.length>100000)throw Error('header 过长');if(/^\.\.\.(?:\s+#.*)?$/.test(t)){const h=autoHeader(st);st.autoPhase='body';return h.map((raw,index)=>raw+(index<st.autoLines.length-1?st.autoLines[index].delimiter:'')).join('')}return null}
 if(!t||t.startsWith('#'))return raw;
 let l=st.layout;const wcol=l?(l.standard?st.opt.w:l.w):st.opt.w,codeColumns=l?(l.standard?[1,st.opt.p]:(l.has?[l.p]:[])):[st.opt.p,st.opt.w+1];
 let row=parseRow(raw,st.opt,wcol,codeColumns);if(row.ambiguous)return note(st,'拼音列有歧义，请改用 Tab 分列',raw);let fields=row.fields;
 // 四种处理方式共用头部与逻辑列，补权重不依赖是否修改拼音或辅助码。
 if(l?.standard)l=defaultRimeLayout(fields,st.no,st.opt);
 else if(!l){l=plainLayout(fields,st.opt);if(l?.ambiguous)return note(st,'拼音列有歧义，请确认拼音列',raw)}
 if(l?.output){padDeclaredFields(fields,l,st.no);}
 const w=l?l.w:st.opt.w,word=(fields[w]||'').replace(/^\uFEFF/,'');
 if(!word)return note(st,'缺少词语列',raw);
 if(!Array.from(word).every(c=>isHan(c)))return note(st,'混合非汉字',raw);
 const target=l||{w,p:st.opt.w+1,has:false};
 const replacedBefore=st.pyReplaced||0;
  const weighted=(text,added=false)=>{const out=fillRowWeight(text,st,{...target,rimeDefault:!!l?.standard},added);notePolyphonic(st,word,out,target,added||(st.pyReplaced||0)>replacedBefore);return out};
 if(l?.has&&!l.insertAt&&(fields[l.p]||'').trim()){
   if(st.opt.mode==='autoOnly'){
    if(st.opt.replacePinyin)return weighted(replacePinyinInRow(raw,st,row,l.w,l.p,false,false));
    return weighted(note(st,'已有拼音保留',raw));
   }
   if(st.opt.mode==='strip')return weighted(stripAuxFromRow(raw,st,row,l.p));
   if(st.opt.mode==='autoAux'&&st.opt.replacePinyin)return weighted(replacePinyinInRow(raw,st,row,l.w,l.p,true,!!st.opt.replace));
   const old=st.opt;st.opt={...old,w:l.w,p:l.p};st.no--;st.phase='body';let result;
   try{result=legacyConvertLine(raw,st,row)}finally{st.opt=old}
   return weighted(result);
 }
 if(st.opt.mode==='existing'||st.opt.mode==='strip')return weighted(note(st,'无 code',raw));
 let syll=generateSyllables(word,st);
 if(!syll)return weighted(note(st,'未识别读音',raw));
 if(st.opt.mode==='autoAux'){const chars=Array.from(word),missing=chars.filter(c=>!st.opt.table.has(c)||!validAux(st.opt.table.get(c)));if(missing.length){missing.forEach(c=>st.missing.add(c));return weighted(note(st,'缺辅助码',raw))}for(let i=0;i<syll.length;i++)syll[i]+=st.opt.aux+st.opt.table.get(chars[i])}
 const py=syll.join(' ');let out;
 if(l?.standard){
  const rest=fields.slice(1);
  if(rest.length){
   // 两列的 Rime 行是 text/weight：把 code 插到 weight 前面，绝不覆盖或重置原权重。
   if(rest.length===1&&/^\d+(?:\.\d+)?$/.test((rest[0]||'').trim()))out=[word,py,rest[0]];
   else{rest[0]=py;out=[word].concat(rest)}
  }else out=[word,py]
 }else{
  out=fields.slice();
  if(l?.has){
   while(out.length<l.p)out.push('');
   if(l.insertAt)out.splice(l.p,0,py);else out[l.p]=py;
  }else{
   const insertAt=l?l.p:w+1;while(out.length<insertAt)out.push('');out.splice(insertAt,0,py);
  }
 }
 if(out.length)out[0]=out[0].replace(/^\uFEFF/,'');
 st.coded++;st.pyAdded=(st.pyAdded||0)+1;if(st.opt.mode==='autoAux')st.auxAdded=(st.auxAdded||0)+1;return weighted((raw.startsWith('\uFEFF')?'\uFEFF':'')+out.join(outSep(st,raw)),true)
}
function convertLine(raw,st,delimiter=''){
 const out=autoConvert(raw,st,delimiter),tab=String.fromCharCode(9);
 if(st.opt.sep==='space'&&out!=null&&!st.layout&&!raw.includes(tab)&&!out.includes(String.fromCharCode(10))&&!out.includes(String.fromCharCode(13))){
  const fields=out.split(tab);
  if(fields.every(field=>field!==''&&field.trim()===field&&field.indexOf(' ')<0))return fields.join(' ');
 }
 return out;
}

async function buildEntry(stream,opt){const rd=stream.getReader(),dec=new TextDecoder(opt.encoding,{fatal:true,ignoreBOM:true}),enc=new TextEncoder(),st=state(opt);let buf='',parts=[],crc=0xffffffff,size=0,pending='';async function flush(){if(!pending)return;const b=enc.encode(pending);parts.push(b);size+=b.length;crc=crcAdd(crc,b);pending='';await new Promise(r=>setTimeout(r,0))}async function feed(text,final=false){buf+=text;let start=0;for(let i=0;i<buf.length;i++){if(buf[i]!=='\r'&&buf[i]!=='\n')continue;if(buf[i]==='\r'&&i===buf.length-1&&!final)break;let end=i,delim=buf[i];if(delim==='\r'&&buf[i+1]==='\n'){delim+='\n';i++}const converted=convertLine(buf.slice(start,end),st,delim);if(converted!==null)pending+=converted+delim;start=i+1;if(pending.length>65536)await flush()}buf=buf.slice(start);if(final&&buf){pending+=convertLine(buf,st);buf=''}await flush()}
 try{for(;;){const r=await rd.read();if(r.done)break;await feed(dec.decode(r.value,{stream:true}))}await feed(dec.decode(),true);if(st.phase==='header'||st.autoPhase==='header')throw Error('Rime header 未找到结束标记 ...');return{parts,crc:(crc^0xffffffff)>>>0,rawSize:size,comp:size,method:0,st}}finally{await rd.cancel().catch(()=>{});rd.releaseLock()}}
async function readZipEntries(file){const tail=new Uint8Array(await file.slice(Math.max(0,file.size-65557)).arrayBuffer()),dv=new DataView(tail.buffer);let e=-1;for(let i=tail.length-22;i>=0;i--)if(dv.getUint32(i,true)===0x06054b50&&i+22+dv.getUint16(i+20,true)===tail.length){e=i;break}if(e<0)throw Error('ZIP 结束目录损坏');const n=dv.getUint16(e+10,true),size=dv.getUint32(e+12,true),off=dv.getUint32(e+16,true);if(dv.getUint16(e+4,true)||dv.getUint16(e+6,true)||dv.getUint16(e+8,true)!==n)throw Error('分卷 ZIP 暂不支持');if(n===65535||size===0xffffffff||off===0xffffffff)throw Error('ZIP64 暂不支持');if(off+size>file.size-tail.length+e)throw Error('ZIP 目录越界');const cd=new Uint8Array(await file.slice(off,off+size).arrayBuffer()),d=new DataView(cd.buffer);let q=0,out=[];for(let i=0;i<n;i++){if(q+46>cd.length||d.getUint32(q,true)!==0x02014b50)throw Error('ZIP 中央目录损坏');const flags=d.getUint16(q+8,true),method=d.getUint16(q+10,true),crc=d.getUint32(q+16,true),csize=d.getUint32(q+20,true),rawSize=d.getUint32(q+24,true),nl=d.getUint16(q+28,true),el=d.getUint16(q+30,true),cl=d.getUint16(q+32,true),lo=d.getUint32(q+42,true);if(q+46+nl+el+cl>cd.length)throw Error('ZIP 条目越界');let nb=cd.slice(q+46,q+46+nl),name;try{name=new TextDecoder(flags&2048?'utf-8':'gb18030',{fatal:true}).decode(nb)}catch{throw Error('ZIP 文件名编码无效')}q+=46+nl+el+cl;if(flags&1)throw Error(name+'：加密 ZIP 暂不支持');if([csize,rawSize,lo].includes(0xffffffff))throw Error(name+'：ZIP64 暂不支持');if(![0,8].includes(method))throw Error(name+'：压缩方法 '+method+' 暂不支持');const h=new Uint8Array(await file.slice(lo,lo+30).arrayBuffer());if(h.length!==30||new DataView(h.buffer).getUint32(0,true)!==0x04034b50)throw Error(name+'：本地头损坏');let hd=new DataView(h.buffer),start=lo+30+hd.getUint16(26,true)+hd.getUint16(28,true);if(start+csize>off)throw Error(name+'：数据越界');out.push({name,crc,csize,size:rawSize,method,blob:file.slice(start,start+csize),open:true})}return out}
function checkedStream(e){let crc=0xffffffff,n=0;let s=e.blob.stream();if(e.method===8)s=s.pipeThrough(new DecompressionStream('deflate-raw'));return s.pipeThrough(new TransformStream({transform(b,c){crc=crcAdd(crc,b);n+=b.length;c.enqueue(b)},flush(){if(n!==e.size||((crc^0xffffffff)>>>0)!==e.crc)throw Error(e.name+'：CRC 或长度校验失败')}}))}
function save(blob,name){let a=document.createElement('a'),u=URL.createObjectURL(blob);a.href=u;a.download=name;document.body.appendChild(a);a.click();a.remove();setTimeout(()=>URL.revokeObjectURL(u),30000)}
function say(x,c=''){ $('msg').textContent=x;$('msg').className='msg '+c}
async function run(files){if(busy)return;busy=true;files=Array.from(files);let reports=[];try{const opt=getOpt();if(opt.mode!=='autoOnly'){await ready;opt.table=tables.get(opt.auxType);if(!opt.table)throw Error('未知辅助码类型：'+opt.auxType);}let outputs=[];$('bar').className='bar';for(const f of files){say('处理中：'+f.name);let entries=/\.zip$/i.test(f.name)?await readZipEntries(f):[{name:f.name,blob:f,open:false}];if(!/\.zip$/i.test(f.name)&&!supported(f.name))throw Error('不支持文件：'+f.name);const isZip=/\.zip$/i.test(f.name);let directBlob=null;let z=isZip?new Zip():null;for(let i=0;i<entries.length;i++){const e=entries[i];$('fill').style.width=((i+1)/entries.length*100)+'%';if(!supported(e.name)){// 非词库条目连同目录保留，不丢资源。
 const rd=checkedStream(e).getReader();try{while(!(await rd.read()).done){}}finally{rd.releaseLock()}z.add(e.name,[new Uint8Array(await e.blob.arrayBuffer())],e.crc,e.size,e.csize,e.method);reports.push(e.name+'：非词库原样保留');continue}
 let r;try{r=await buildEntry(e.open?checkedStream(e):e.blob.stream(),opt)}catch(err){throw Error(e.name+'：'+err.message)}reports.push(e.name+'：'+r.st.no+' 行，已处理 '+r.st.coded+' 行\n'+JSON.stringify(r.st.counts)+'\n'+r.st.samples.join('\n')+(r.st.missing.size?'\n缺码字：'+[...r.st.missing].join(''):''));if(isZip){z.add(e.name,r.parts,r.crc,r.rawSize,r.comp,r.method)}else{if(r.method!==0)throw Error('直接导出需要未压缩文本');directBlob=new Blob(r.parts,{type:/\.ya?ml$/i.test(f.name)?'application/yaml;charset=utf-8':'text/plain;charset=utf-8'})}}outputs.push({name:f.name,blob:isZip?z.finish():directBlob})}
 // 普通文件直接按原名导出；ZIP 输入仍分别输出 ZIP。
 for(const o of outputs){const name=o.name;const a=document.createElement('a');a.textContent='再次下载：'+name;a.href=URL.createObjectURL(o.blob);a.download=name;a.style.display='block';$('downloads').appendChild(a);save(o.blob,name)}say('完成：'+outputs.length+' 个输出文件；普通文件直接导出，ZIP 仍为 ZIP；词库输出 UTF-8，文件名和 ZIP 相对路径保持原样。','ok')
 }catch(e){say('转换失败：'+e.message,'err');reports.push('错误：'+e.message)}finally{busy=false;$('bar').className='bar hide';$('report').textContent=reports.join('\n\n');$('detail').className=''}}
function isHan(c){var cp=c.codePointAt(0);return cp===0x3007||(cp>=0x3400&&cp<=0x4DBF)||(cp>=0x4E00&&cp<=0x9FFF)||(cp>=0x20000&&cp<=0x2EBEF)||(cp>=0x30000&&cp<=0x3134F);}
var __st=null;var __tables={};
function __setTable(id,json){var o=JSON.parse(json);var m=new Map();for(var k in o)m.set(k,o[k]);__tables[id]=m;}
function __start(auxType,mode,toneType,sep,encoding,replace,aux,w,p,weight,replacePinyin){if(!Number.isInteger(w)||!Number.isInteger(p)||w<0||p<0||w>=128||p>=128||w===p)throw Error('列号必须是不同的 1–128 正整数');var opt={mode:mode,toneType:toneType,sep:sep,encoding:encoding,replace:replace,replacePinyin:replacePinyin===true||replacePinyin==='true',aux:aux,auxType:auxType,w:w,p:p,weight:String(weight||'120'),table:__tables[auxType]||new Map()};__st=state(opt);}
function __line(line){return convertLine(line,__st);}
function __lines(lines){var out=[];for(var i=0;i<lines.length;i++)out.push(convertLine(lines[i].text,__st,lines[i].delimiter));return JSON.stringify(out);}
// The Android bridge drains details in bounded batches without limiting the file total.
function __takePolyphonicDetails(limit){var details=__st.polyphonicDetails.splice(0,limit);__st.polyphonicSamples.splice(0,details.length);return JSON.stringify(details);}
function __clear(){__st=null}
function __report(){var r={no:__st.no,coded:__st.coded,counts:__st.counts,samples:__st.samples,missing:Array.from(__st.missing),pyAdded:__st.pyAdded||0,auxAdded:__st.auxAdded||0,auxStripped:__st.auxStripped||0,polyphonic:__st.polyphonic||0,polyphonicSamples:__st.polyphonicSamples||[],polyphonicDetails:__st.polyphonicDetails||[],phase:__st.phase,autoPhase:__st.autoPhase};if(__st.pyReplaced)r.pyReplaced=__st.pyReplaced;if(r.phase==='header'||r.autoPhase==='header')throw Error('Rime 头部缺少结束标记 ...');return JSON.stringify(r);}

// Offline JavaScript/DOM-stub tests. No browser, server, downloads or network access.
const fs=require('node:fs'),vm=require('node:vm'),assert=require('node:assert/strict');
const path=require('node:path');
const fixture=process.argv[2], output=process.argv[3];
const source=fs.readFileSync(fixture,'utf8').match(/<script>([\s\S]*?)<\/script>/)[1];
const elements=[];
class Element {
 constructor(tag){this.tagName=tag;this.children=[];this.dataset={};this.listeners={};this.textContent='';this.value='';elements.push(this)}
 append(...children){this.children.push(...children)}
 setAttribute(name,value){this[name]=value}
 addEventListener(name,fn){this.listeners[name]=fn}
 click(){if(this.onclick)this.onclick();if(this.tagName==='a')downloads.push(this)}
}
const ids={},store={},downloads=[],blobs=[];
for(const name of ['run','nav','queries','metrics','progress','message','save','export','exportEvidence','import','file'])ids[name]=new Element('div');
const context={document:{createElement:t=>new Element(t),getElementById:id=>ids[id],querySelectorAll:s=>elements.filter(e=>s==='select[data-key]'?e.tagName==='select'&&e.dataset.key:e.tagName==='textarea'&&e.dataset.note)},
 localStorage:{getItem:k=>store[k]??null,setItem:(k,v)=>store[k]=v},TextDecoder,Uint8Array,atob,Blob,Date,console,
 URL:{createObjectURL:b=>{blobs.push(b);return 'blob:offline'},revokeObjectURL:()=>{}},setTimeout:fn=>fn()};
vm.createContext(context);vm.runInContext(source,context);
let tests=0;const test=fn=>{fn();tests++},run=s=>vm.runInContext(s,context);
test(()=>{assert.equal(run('Object.keys(labels).length'),0);assert.equal(run('payload().metrics.status'),'PENDING_COMPLETE_HUMAN_LABELS');assert.equal(run('payload().metrics.totals'),undefined)});
test(()=>{assert.equal(elements.filter(e=>e.tagName==='select').length,2);assert.equal(elements.filter(e=>e.tagName==='select').every(e=>!e.value),true)});
test(()=>{assert.equal(elements.some(e=>e.tagName==='h2'&&e.textContent.startsWith('OFFLINE-EXAMPLE')),true);assert.equal(elements.some(e=>e.tagName==='p'&&e.textContent.includes('<script>invalid</script>')),true)});
const selects=elements.filter(e=>e.tagName==='select'),notes=elements.filter(e=>e.tagName==='textarea');
test(()=>{selects[0].value='2';selects[0].listeners.change();assert.equal(run('metrics().totals.V1.direct'),1);assert.equal(Object.keys(store).length,1);assert.equal(run('payload().metrics.totals'),undefined)});
test(()=>{selects[1].value='1';selects[1].listeners.change();assert.equal(run('metrics().status'),'COMPLETE_HUMAN_LABELS');assert.equal(run('metrics().partialDeleted'),1);assert.equal(run('metrics().directDeleted'),0);assert.equal(run('metrics().usefulDeleted'),1);assert.equal(run('metrics().totals.V0.partial'),1)});
test(()=>{notes[1].value='chunk boundary note';notes[1].listeners.input();assert.equal(run('payload().humanLabels[key(evidence.queries[0],evidence.queries[0].rawTopK5[1])].note'),'chunk boundary note')});
test(()=>{const before=run('JSON.stringify(labels)');assert.throws(()=>run("apply({schemaVersion:'rag-p1-human-annotations-v1',evidenceSha256:'wrong',humanLabels:{}})"));assert.equal(run('JSON.stringify(labels)'),before)});
test(()=>{assert.throws(()=>run("apply({...payload(),humanLabels:{unknown:{label:'2'}}})"));assert.throws(()=>run("apply({...payload(),humanLabels:{[Array.from(keys)[0]]:{label:'3'}}})"))});
test(()=>{const before=run('JSON.stringify(payload().humanLabels)');run('apply(payload())');assert.equal(run('JSON.stringify(payload().humanLabels)'),before);assert.equal(selects[0].value,'2')});
test(()=>{selects[1].value='U';selects[1].listeners.change();assert.equal(run('payload().metrics.status'),'PENDING_COMPLETE_HUMAN_LABELS');assert.equal(run('metrics().undecidable'),1);assert.equal(run('metrics().totals.V0.irrelevant'),0)});
test(()=>{selects[1].value='0';selects[1].listeners.change();assert.equal(run('metrics().irrelevantRetentionRatio'),0);assert.equal(run('metrics().usefulDeleted'),0)});
test(()=>{ids.export.onclick();assert.equal(downloads.at(-1).download,'human-annotations.json');ids.exportEvidence.onclick();assert.equal(downloads.at(-1).download,'results.json');assert.equal(run('JSON.stringify(evidence.queries[0]).includes("humanLabels")'),false)});
(async()=>{
 const raw=await blobs.at(-1).text();assert.equal(raw,fs.readFileSync(path.join(path.dirname(fixture),'results.json'),'utf8'));tests++;
 const labels=JSON.parse(await blobs.at(-2).text());assert.equal(labels.groundTruthSource,'HUMAN_ONLY');assert.deepEqual(labels.aiSuggestions,[]);assert.equal(labels.metrics.status,'COMPLETE_HUMAN_LABELS');tests++;
 const summary={tests,failures:0,errors:0,skipped:0,networkCalls:0,browserVisualVerification:'NOT_PERFORMED_FILE_URL_BLOCKED_BY_BROWSER_TOOL',fixture};
 fs.writeFileSync(output,JSON.stringify(summary,null,2));console.log(JSON.stringify(summary));
})().catch(error=>{console.error(error);process.exitCode=1});

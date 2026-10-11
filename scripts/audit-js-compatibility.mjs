import fs from 'node:fs';
import path from 'node:path';
import vm from 'node:vm';
const root=path.resolve(import.meta.dirname,'..');
const store=fs.readFileSync(path.join(root,'app/src/main/kotlin/org/koitharu/kotatsu/sourcerepo/data/SourceRepoStore.kt'),'utf8');
const indexes=[...new Set([...store.matchAll(/https:\/\/[^"\s]+(?:index|anime_index|novel_index)\.json/g)].map(x=>x[0]))];
const timeout=Number(process.argv[2]||12000);
async function get(url) {const res=await fetch(url,{signal:AbortSignal.timeout(timeout)});if(!res.ok)throw Error('HTTP '+res.status);return res.text();}
const sources=new Map();const errors=[];
for(const url of indexes){try{const items=JSON.parse(await get(url));for(const item of items){if(item?.id==null||!item.sourceCodeUrl)continue;const js=item.sourceCodeLanguage===1||(item.sourceCodeLanguage==null&&item.sourceCodeUrl.endsWith('.js'));if(js&&!sources.has(String(item.id)))sources.set(String(item.id),item);}}catch(e){errors.push({repo:url,error:String(e)});}}
const entries=[...sources.values()];const results=[];let cursor=0;
async function worker(){while(cursor<entries.length){const e=entries[cursor++];let status='STATIC_OK_UNTESTED',detail='';
try{const code=await get(e.sourceCodeUrl);new vm.Script(code,{filename:e.name+'.js'});if(!/\bclass\s+DefaultExtension\b|\bDefaultExtension\s*=/.test(code)){status='ENTRYPOINT_MISSING';detail='DefaultExtension not declared';}}
catch(err){detail=String(err);status=err instanceof SyntaxError?'SYNTAX_ERROR':'DOWNLOAD_ERROR';}
results.push({id:e.id,name:e.name,baseUrl:e.baseUrl,codeUrl:e.sourceCodeUrl,status,detail});}}
await Promise.all(Array.from({length:8},worker));
const output=path.join(root,'source-audit');fs.mkdirSync(output,{recursive:true});
const csv=v=>'"'+String(v??'').replaceAll('"','""')+'"';
fs.writeFileSync(path.join(output,'js-compatibility.csv'),['id,name,baseUrl,codeUrl,status,detail',...results.map(r=>[r.id,r.name,r.baseUrl,r.codeUrl,r.status,r.detail].map(csv).join(','))].join('\n'),'utf8');
console.log(JSON.stringify({indexes:indexes.length,uniqueJs:entries.length,results:results.reduce((a,r)=>(a[r.status]=(a[r.status]||0)+1,a),{}),repoErrors:errors},null,2));
console.log('Static checks only: no QuickJS execution, search, chapter or playback verification.');

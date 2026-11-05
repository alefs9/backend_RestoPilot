/* Ejecuta la colección final con fixtures SQL exclusivamente en PostgreSQL LOCAL de auditoría. */
const fs=require('fs'),path=require('path'),cp=require('child_process');
const root=path.resolve(__dirname,'..');
let newman;
for(const location of ['newman',path.join(root,'target/postman-runner/node_modules/newman'),path.join(root,'target/reauditoria-2026-10-15/newman/node_modules/newman')]){
    try{newman=require(location);break;}catch(e){if(e.code!=='MODULE_NOT_FOUND')throw e;}
}
if(!newman){console.error('Instala Newman: npm.cmd install --prefix target/postman-runner --no-save newman@6.2.3');process.exit(1);}
const args=process.argv.slice(2),opt=name=>{const i=args.indexOf(name);return i<0?undefined:args[i+1];};
const baseUrl=opt('--baseUrl')||'http://localhost:18082';
const url=new URL(baseUrl);
if(url.protocol!=='http:'||!['localhost','127.0.0.1'].includes(url.hostname)||url.pathname!=='/'||url.search||url.hash){throw new Error('Solo se admite HTTP local sin ruta adicional');}
const backend=opt('--backend')||'restopilot-backend-1';
const db='restopilot-postgres-audit';
const docker=argv=>cp.execFileSync('docker',argv,{encoding:'utf8',maxBuffer:10*1024*1024,stdio:['pipe','pipe','pipe']});
let meta,pg;
try{meta=JSON.parse(docker(['inspect',backend]))[0];pg=JSON.parse(docker(['inspect',db]))[0];}
catch{console.error('No se pudo inspeccionar el backend y PostgreSQL de auditoría.');process.exit(1);}
const ports=meta.NetworkSettings.Ports?.['8081/tcp']||[];
if(meta.State.Status!=='running'||!ports.some(p=>p.HostPort===(url.port||'80'))||!meta.Config.Env.includes('DB_URL=jdbc:postgresql://host.docker.internal:55439/restopilot_audit')||pg.State.Status!=='running'||!pg.Config.Env.includes('POSTGRES_DB=restopilot_audit')||!pg.Config.Env.includes('POSTGRES_USER=restopilot_audit')){
    console.error('No coincide el destino con el backend y PostgreSQL exclusivos de auditoría. No se ejecutó la colección.');process.exit(1);
}
const identidad=docker(['exec',db,'psql','-X','-At','-U','restopilot_audit','-d','restopilot_audit','-c',"SELECT current_database() || '|' || current_user"]).trim();
if(identidad!=='restopilot_audit|restopilot_audit')throw Error('Identidad de base inesperada');
const created=new Set(),fixtureErrors=[];
const output=path.join(root,'auditoria','ciclo-reservas-2026-10-17');fs.mkdirSync(output,{recursive:true});
const options={collection:path.join(root,'postman/postman_collection.json'),environment:{values:[{key:'baseUrl',value:baseUrl,enabled:true},{key:'cicloFixturesSql',value:'true',enabled:true}]},reporters:[],bail:true};
if(opt('--folder'))options.folder=opt('--folder');
const execution=newman.run(options,(error,summary)=>{
    const safe=x=>String(x||'').replace(/eyJ[A-Za-z0-9_-]+\.[A-Za-z0-9_-]+\.[A-Za-z0-9_-]+/g,'[JWT OCULTO]');
    const report={target:baseUrl,database:'restopilot_audit',schema:'public',backendImage:meta.Image,
        stats:summary?.run?.stats,fixtureErrors,
        failures:summary?.run?.failures?.map(f=>({request:f.source?.name,error:safe(f.error?.message)}))||[],
        requests:summary?.run?.executions?.map(e=>({name:e.item.name,status:e.response?.code,assertions:e.assertions?.map(a=>({name:a.assertion,failed:!!a.error}))}))||[]};
    report.exitCode=error||fixtureErrors.length||report.failures.length?1:0;
    const name=!options.folder?'newman-completo-sanitizado.json':options.folder.startsWith('04 ')?'newman-ciclo-sanitizado.json':options.folder.startsWith('05 ')?'newman-crud-sanitizado.json':'newman-carpeta-sanitizado.json';
    fs.writeFileSync(path.join(output,name),JSON.stringify(report,null,2));
    console.log(JSON.stringify({exitCode:report.exitCode,stats:report.stats,fixtureErrors:report.fixtureErrors,failures:report.failures,report:path.relative(root,path.join(output,name))},null,2));
    process.exitCode=report.exitCode;
});
execution.on('request',(error,args)=>{
    if(error||!args.response)return;
    const name=args.item.name;
    if(/^(C0[123] crear reserva fixture|CICLO FIXTURE EXP_P crear|EXP_C crear reserva)$/.test(name)&&args.response.code===201){
        created.add(args.response.json().id);
    }
    if(!/^CICLO FIXTURE (C0[123] confirmar|EXP_P crear|EXP_C confirmar)$/.test(name))return;
    try{
        const id=args.response.json().id;
        if(!Number.isSafeInteger(id)||!created.has(id))throw Error('Fixture no creado en esta corrida');
        const pending=name.includes('EXP_P'),expired=name.includes('EXP_C');
        const offset=pending?'1 minute':expired?'16 minutes':'5 minutes';
        const start=pending||expired?`ahora - interval '${offset}'`:`greatest(date_trunc('day',ahora), ahora - interval '${offset}')`;
        const state=pending?'PENDIENTE':'CONFIRMADA';
        const sql=`BEGIN; SET LOCAL TIME ZONE 'America/Lima'; WITH reloj AS (SELECT localtimestamp AS ahora), horario AS (SELECT ${start} AS inicio, ahora FROM reloj) UPDATE public.reservas r SET fecha=horario.inicio::date, hora_inicio=horario.inicio::time, hora_fin=least(date_trunc('day',horario.inicio)+interval '23 hours 59 minutes 59 seconds',horario.ahora+interval '1 hour')::time FROM horario WHERE r.id=${id} AND r.estado='${state}' AND r.llegada_registrada_en IS NULL RETURNING r.id; COMMIT;`;
        const result=docker(['exec',db,'psql','-X','-At','-v','ON_ERROR_STOP=1','-U','restopilot_audit','-d','restopilot_audit','-c',sql]);
        if(!result.split(/\r?\n/).includes(String(id)))throw Error('Fixture no actualizado');
    }catch(e){fixtureErrors.push({request:name,error:'Falló el fixture SQL exclusivo de auditoría. Verificar migración y esquema.'});execution.abort();}
});

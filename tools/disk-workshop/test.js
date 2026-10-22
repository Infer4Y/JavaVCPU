globalThis.runDiskTests = function () {
  const D=DosDisk,check=(condition,message)=>{if(!condition)throw new Error(message);};
  const rejects=fn=>{let threw=false;try{fn();}catch{threw=true;}check(threw,'Expected rejection');};
  const bytes=new Uint8Array(D.SIZE);bytes.set([74,86,67,80,77,4,253,1]);bytes[1000]=77;
  const disk=D.Disk.format(bytes);disk.put('HELLO.TXT',D.textBytes('Hello!\r\nA\tB'),1);
  check(D.textOf(disk.list()[0].data)==='Hello!\nA    B','Text normalization');
  check(disk.bytes[1000]===77&&bytes[D.BASE]===0,'Original/boot preservation');
  rejects(()=>disk.put('BIG.TXT',new Uint8Array(D.MAX+1),1));rejects(()=>D.textBytes('é'));
  const rgba=new Uint8Array([255,0,0,255,0,255,0,255,0,0,255,255,255,255,255,255]);
  const image=D.image(2,2,rgba);check(D.decodeImage(image).pixels.join()==='96,28,3,127','Palette conversion');disk.put('PIC.IMG',image,2);
  const largeImage=D.image(240,240,new Uint8Array(240*240*4).fill(255));
  check(largeImage.length===57604&&D.decodeImage(largeImage).pixels.every(c=>c===127),'Maximum image round trip');
  rejects(()=>D.image(241,1,new Uint8Array(241*4)));
  rejects(()=>D.decodeImage(largeImage.slice(0,-1)));
  const badPixels=largeImage.slice();badPixels[300]=128;rejects(()=>D.decodeImage(badPixels));

  const tune=D.notes('60 3\n64 3\n67 6');check(D.decodeTune(tune).length===6,'Tune composer');disk.put('TUNE.JMT',tune,3);
  const file=track=>Uint8Array.from([77,84,104,100,0,0,0,6,0,0,0,1,1,224,77,84,114,107,0,0,track.length>>8,track.length&255,...track]);
  const midi=file([0,144,60,100,131,96,128,60,0,0,255,47,0]);
  const events=D.decodeTune(D.midi(midi));check(events.length===2&&events[0].frames===15&&events[1].control===0,'MIDI note-on/off');
  const running=D.decodeTune(D.midi(file([0,144,60,100,131,96,60,0,0,255,47,0])));check(running[1].control===0,'Running status');
  const tempo=D.decodeTune(D.midi(file([0,255,81,3,3,208,144,0,144,60,100,131,96,128,60,0,0,255,47,0])));check(tempo[0].frames===8,'Tempo conversion');
  const long=D.decodeTune(D.midi(file([0,144,60,100,129,160,0,128,60,0,0,255,47,0])));check(long.reduce((n,e)=>n+e.frames,0)===646,'Long duration splitting');
  rejects(()=>D.midi(midi.slice(0,-1)));
  const dense=D.decodeTune(D.midi(file([
    ...Array.from({length:8},(_,i)=>[0,144,60+i,100]).flat(),
    131,96,144,68,100,131,96,128,60,0,131,96,128,68,0,
    0,144,69,100,131,96,144,70,100,0,255,47,0
  ])));
  check(dense.slice(0,8).every((e,i)=>e.voice===i),'First eight notes use distinct voices');
  check(dense[8].note===68&&dense[8].voice===0&&dense[8].control===129,'Ninth note replaces oldest voice');
  check(dense[8].frames===30&&dense[9].note===68&&dense[9].control===0,'Stolen note release preserves replacement and timing');
  check(dense[10].note===69&&dense[10].voice===0,'Released voice is reused');
  check(dense[11].note===70&&dense[11].voice===1,'Next overflow replaces oldest remaining note');
  check(dense.every(e=>e.voice>=0&&e.voice<8)&&dense.slice(-8).every(e=>e.control===0),'All remaining voices stop');
  const voice7=D.tune([{frames:1,voice:7,note:60,control:129}]);
  check(D.decodeTune(voice7)[0].voice===7,'Eighth voice round trip');
  rejects(()=>D.tune([{frames:1,voice:16,note:60,control:129}]));
  const extended=D.tune(Array.from({length:20000},()=>({frames:0,voice:0,note:60,control:129})));
  check(extended.length===80004&&extended[2]===2&&D.decodeTune(extended).length===20000,'Tune exceeds old 64 KiB boundary');
  rejects(()=>D.decodeTune(extended.slice(0,-1)));
  const invalidHeader=extended.slice();invalidHeader[3]=1;rejects(()=>D.decodeTune(invalidHeader));
  disk.remove(disk.list()[0].slot);check(disk.list().length===2,'Delete');
  for(let i=0;i<300;i++)disk.put(`F${i}.TXT`,D.textBytes('X'),1);
  check(new D.Disk(disk.bytes).list().length===302,'Directories grow beyond 255 entries');
  for(const length of [0,1023,1024,1025,65536,140000,1048576]){
    const payload=Uint8Array.from({length},(_,i)=>i&255);disk.put('LARGE.BIN',payload);
    const entry=disk.list().find(f=>f.name==='LARGE.BIN');
    check(entry.data.length===length&&entry.data.every((b,i)=>b===payload[i]),'Cluster round trip '+length);
    check(entry.blocks.length===Math.ceil(length/1024),'Cluster count '+length);
  }
  const docs=disk.mkdir('DOCS');disk.put('SAME.TXT',D.textBytes('ROOT'),1);disk.cwd=docs;
  disk.put('SAME.TXT',D.textBytes('CHILD'),1);check(disk.path()==='/DOCS'&&disk.parent()===D.ROOT,'Parent navigation');
  check(disk.clone().cwd===docs,'Clone folder');disk.cwd=D.ROOT;
  rejects(()=>disk.remove(disk.list().find(f=>f.name==='DOCS').slot));
  const nested=disk.mkdir('NESTED');disk.move(disk.list().find(f=>f.name==='NESTED').slot,docs);
  rejects(()=>disk.move(disk.list().find(f=>f.name==='DOCS').slot,nested));
  check(new D.Disk(disk.bytes).validate(),'Moved directories remain consistent');
  const full=D.Disk.format(bytes);full.put('OLD.TXT',D.textBytes('KEEP'),1);
  full.put('FILL.BIN',new Uint8Array(full.capacity().freeBytes));const before=full.bytes.slice();
  rejects(()=>full.put('OLD.TXT',new Uint8Array(2048),1));
  check(before.every((b,i)=>b===full.bytes[i]),'Full disk replacement preserves every byte');
  rejects(()=>full.put('NEW.TXT',D.textBytes('X'),1));
  const corrupt=disk.bytes.slice(),entry=disk.list().find(f=>f.name==='LARGE.BIN');
  corrupt[D.FAT+entry.first*2]=entry.first&255;corrupt[D.FAT+entry.first*2+1]=entry.first>>>8;
  rejects(()=>new D.Disk(corrupt));
  if(typeof DosDesktop!=='undefined') {
    const payload=Uint8Array.from(atob(DosDesktop),c=>c.charCodeAt(0));disk.put('JCTRL.COM',payload);
    check(disk.list().find(f=>f.name==='JCTRL.COM').data.every((b,i)=>b===payload[i]),'Bundled desktop import');
  }
  if(typeof DosDesktopBundle!=='undefined') {
    const clean=D.Disk.format(bytes),installed=D.installBundle(clean,DosDesktopBundle);
    check(clean.list().length===0,'Bundle leaves input disk untouched');
    check(installed.list().map(f=>f.name).join()==='JCTRL.COM,TEXTEDIT.COM,PAINT.COM','Complete desktop bundle');
    check(D.installBundle(installed,DosDesktopBundle).bytes.every((b,i)=>b===installed.bytes[i]),'Idempotent bundle');
    clean.put('PAINT.COM',Uint8Array.of(4));const before=clean.bytes.slice();rejects(()=>D.installBundle(clean,DosDesktopBundle));
    check(before.every((b,i)=>b===clean.bytes[i]),'Bundle conflict makes no partial changes');
    const full=D.Disk.format(bytes);full.put('FILL.BIN',new Uint8Array(full.capacity().freeBytes));
    const snapshot=full.bytes.slice();rejects(()=>D.installBundle(full,DosDesktopBundle));check(snapshot.every((b,i)=>b===full.bytes[i]),'Full bundle leaves disk intact');
  }
  return 'PASS: JVFS large files, growing directories, parent navigation, moves/cycles, full-disk preservation, converters and corruption checks.';
};

/* JVFS 1 flat-volume filesystem and converters. No network or host VM hooks. */
globalThis.DosDisk = (() => {
  const SIZE=1<<24, BASE=2<<16, BLOCK=1024, MAX=(16384-162)*1024;
  const types=['Binary / COM','Text / JVS','Palette image','MIDI notes','Directory'];
  const fail=message=>{throw new Error(message);};
  const integer=(value,min,max,label)=>{if(!Number.isInteger(value)||value<min||value>max)fail(`${label} must be ${min}–${max}.`);return value;};
  function textBytes(text) {
    text=text.replace(/\r\n?/g,'\n').replace(/\t/g,'    ');
    if([...text].some(c=>c.charCodeAt(0)>126 || (c.charCodeAt(0)<32&&c!=='\n'))) fail('DOS text supports ASCII characters and newlines. Tabs become four spaces.');
    return Uint8Array.from([...text],c=>c.charCodeAt(0));
  }
  const textOf=bytes=>Array.from(bytes,b=>String.fromCharCode(b)).join('');
  function nameOf(name,extension) {
    const base=name.replace(/\.[^.]*$/,'').toUpperCase().replace(/[^A-Z0-9_-]/g,'_').slice(0,8)||'FILE';
    return base+'.'+extension.toUpperCase().slice(0,3);
  }
  const SUPER=0x20000,FAT=0x20400,ROOT=161,CLUSTERS=16384,END=65535,RESERVED=65534;
  const signature=Uint8Array.of(74,86,70,83,1,10,161,0);
  const validName=name=>{name=name.toUpperCase();if(!/^[A-Z0-9_-][A-Z0-9_.-]{0,11}$/.test(name))fail('Names use 1-12 uppercase letters, digits, dots, underscores or hyphens.');return name;};
  class Disk {
    constructor(bytes){
      if(bytes.length!==SIZE)fail('Select a 16 MiB JavaVCPU disk.');
      if(!signature.every((b,i)=>bytes[SUPER+i]===b))fail('This disk needs JVFS 1. Start the updated VM once to format an old test image.');
      this.bytes=new Uint8Array(bytes);this.cwd=ROOT;this.validate();
    }
    static format(bytes=new Uint8Array(SIZE)) {
      if(bytes.length!==SIZE)fail('Expected a 16 MiB disk.');const out=new Uint8Array(bytes);
      out.fill(0,SUPER,FAT+CLUSTERS*2);out.fill(0,ROOT*BLOCK,(ROOT+1)*BLOCK);out.fill(0,256,296);
      const put=(p,v)=>{out[p]=v&255;out[p+1]=v>>>8;};
      for(let c=0;c<ROOT;c++)put(FAT+c*2,RESERVED);put(FAT+ROOT*2,END);
      for(const [n,name] of ['.','..'].entries()){const p=ROOT*BLOCK+n*32;out.set(textBytes(name),p);out[p+12]=4;put(p+14,ROOT);}
      out.set(signature,SUPER);return new Disk(out);
    }
    clone(){const copy=new Disk(this.bytes);copy.cwd=this.cwd;return copy;}
    word(p){return this.bytes[p]|this.bytes[p+1]<<8;}
    wordPut(p,value){this.bytes[p]=value&255;this.bytes[p+1]=value>>>8;}
    fat(c){integer(c,0,CLUSTERS-1,'Cluster');return this.word(FAT+c*2);}
    fatPut(c,next){integer(c,ROOT,CLUSTERS-1,'Data cluster');this.wordPut(FAT+c*2,next);}
    chain(first){const out=[],seen=new Set();for(let c=first;c!==0&&c!==END;c=this.fat(c)){if(c<ROOT||c>=CLUSTERS||seen.has(c))fail('Invalid or cyclic cluster chain.');seen.add(c);out.push(c);if([0,RESERVED].includes(this.fat(c)))fail('Unallocated cluster in a file.');}return out;}
    entry(p){integer(p,ROOT*BLOCK,SIZE-32,'Entry address');if(p%32)fail('Unaligned entry.');let name='';for(let i=0;i<12&&this.bytes[p+i];i++)name+=String.fromCharCode(this.bytes[p+i]);return {slot:p,name,type:this.bytes[p+12],first:this.word(p+14),length:this.bytes[p+16]+this.bytes[p+17]*256+this.bytes[p+18]*65536};}
    entries(directory=this.cwd){const out=[];for(const c of this.chain(directory))for(let n=0;n<32;n++){const p=c*BLOCK+n*32;if(this.bytes[p])out.push(this.entry(p));}return out.filter(f=>!['.','..'].includes(f.name));}
    read(file){if(file.type===4)return new Uint8Array();const blocks=this.chain(file.first);if(blocks.length!==Math.ceil(file.length/BLOCK))fail('File length does not match its chain.');const out=new Uint8Array(file.length);let at=0;for(const c of blocks){const count=Math.min(BLOCK,out.length-at);out.set(this.bytes.subarray(c*BLOCK,c*BLOCK+count),at);at+=count;}return out;}
    list(){return this.entries().map(f=>({...f,parent:this.cwd,data:this.read(f),blocks:this.chain(f.first)}));}
    all(){const out=[],todo=[ROOT],seen=new Set();while(todo.length){const dir=todo.pop();if(seen.has(dir))fail('Cyclic directory.');seen.add(dir);for(const f of this.entries(dir)){out.push({...f,parent:dir,data:this.read(f),blocks:this.chain(f.first)});if(f.type===4)todo.push(f.first);}}return out;}
    parent(directory=this.cwd){return this.word(directory*BLOCK+46);}
    path(){const names=[],seen=new Set();for(let c=this.cwd;c!==ROOT;c=this.parent(c)){if(seen.has(c))fail('Cyclic directory.');seen.add(c);const p=this.parent(c),entry=this.entries(p).find(f=>f.type===4&&f.first===c);if(!entry)fail('Missing parent directory.');names.unshift(entry.name);}return '/'+names.join('/');}
    capacity(){let free=0;for(let c=ROOT;c<CLUSTERS;c++)if(!this.fat(c))free++;return {used:CLUSTERS-ROOT-free,free,total:CLUSTERS-ROOT,freeBytes:free*BLOCK};}
    free(count){const out=[];for(let c=ROOT+1;c<CLUSTERS&&out.length<count;c++)if(!this.fat(c))out.push(c);if(out.length!==count)fail('Disk full. File unchanged.');return out;}
    slotPlan(directory){const blocks=this.chain(directory);for(const c of blocks)for(let n=0;n<32;n++){const p=c*BLOCK+n*32;if(!this.bytes[p])return {slot:p};}return {tail:blocks.at(-1)};}
    grow(plan,c){this.fatPut(c,END);this.bytes.fill(0,c*BLOCK,(c+1)*BLOCK);this.fatPut(plan.tail,c);return c*BLOCK;}
    writeEntry(p,name,type,first,length){this.bytes.fill(0,p,p+32);this.bytes.set(textBytes(name),p);this.bytes[p+12]=type;this.wordPut(p+14,first);for(let i=0;i<3;i++)this.bytes[p+16+i]=(length>>>(i*8))&255;}
    put(name,data,type=0){
      name=validName(name);integer(type,0,3,'File type');if(data.length>MAX)fail('File exceeds the disk capacity.');if(type===2)decodeImage(data);if(type===3)decodeTune(data);
      const old=this.entries().find(f=>f.name===name);if(old?.type===4)fail('Cannot replace a directory.');
      const oldBlocks=old?this.chain(old.first):[],plan=old?{slot:old.slot}:this.slotPlan(this.cwd),needed=Math.ceil(data.length/BLOCK),free=this.free(needed+(plan.slot===undefined?1:0));
      const blocks=free.slice(0,needed);for(let i=0;i<blocks.length;i++)this.fatPut(blocks[i],blocks[i+1]??END);
      const slot=plan.slot??this.grow(plan,free.at(-1));for(let i=0;i<blocks.length;i++){const p=blocks[i]*BLOCK;this.bytes.fill(0,p,p+BLOCK);this.bytes.set(data.subarray(i*BLOCK,(i+1)*BLOCK),p);}
      this.writeEntry(slot,name,type,blocks[0]??0,data.length);for(const c of oldBlocks)this.fatPut(c,0);return slot;
    }
    importFile(name,data,type=0){return {directory:this.cwd,slot:this.put(name,data,type)};}
    mkdir(name){name=validName(name);if(this.entries().some(f=>f.name===name))fail('Destination exists.');const plan=this.slotPlan(this.cwd),free=this.free(1+(plan.slot===undefined?1:0)),c=free[0];this.fatPut(c,END);this.bytes.fill(0,c*BLOCK,(c+1)*BLOCK);this.writeEntry(c*BLOCK,'.',4,c,0);this.writeEntry(c*BLOCK+32,'..',4,this.cwd,0);const slot=plan.slot??this.grow(plan,free[1]);this.writeEntry(slot,name,4,c,0);return c;}
    remove(slot){const f=this.entries().find(f=>f.slot===slot);if(!f)fail('Select a file in this folder.');if(f.type===4&&this.entries(f.first).length)fail('Directory is not empty.');const blocks=this.chain(f.first);this.bytes[slot]=0;for(const c of blocks)this.fatPut(c,0);}
    move(slot,target,name){const f=this.entries().find(f=>f.slot===slot);if(!f)fail('Missing source.');name=validName(name??f.name);if(this.entries(target).some(e=>e.name===name))fail('Destination exists.');if(f.type===4){const seen=new Set();for(let c=target;;c=this.parent(c)){if(c===f.first||seen.has(c))fail('Directory cycle.');seen.add(c);if(c===ROOT)break;}}
      const plan=target===this.cwd?{slot}:this.slotPlan(target),p=plan.slot??this.grow(plan,this.free(1)[0]);this.writeEntry(p,name,f.type,f.first,f.length);if(p!==slot)this.bytes[slot]=0;if(f.type===4)this.wordPut(f.first*BLOCK+46,target);
    }
    validate(){
      for(let c=0;c<ROOT;c++)if(this.fat(c)!==RESERVED)fail('Invalid reserved cluster.');
      const used=new Set(),dirs=[{id:ROOT,parent:ROOT}],seen=new Set();
      const claim=blocks=>{for(const c of blocks){if(used.has(c))fail('Shared cluster chain.');used.add(c);}};
      while(dirs.length){const {id,parent}=dirs.pop();if(seen.has(id))fail('Directory cycle.');seen.add(id);claim(this.chain(id));if(this.word(id*BLOCK+14)!==id||this.parent(id)!==parent)fail('Invalid directory parent.');const names=new Set();
        for(const f of this.entries(id)){validName(f.name);integer(f.type,0,4,'Entry type');if(names.has(f.name))fail('Duplicate filename.');names.add(f.name);if(f.type===4){if(f.length)fail('Directory has a file length.');dirs.push({id:f.first,parent:id});}else{const blocks=this.chain(f.first);if(blocks.length!==Math.ceil(f.length/BLOCK))fail('File length/chain mismatch.');claim(blocks);}}
      }
      for(let c=ROOT;c<CLUSTERS;c++)if(this.fat(c)&&!used.has(c))fail('Orphaned allocated cluster.');return true;
    }
  }

  function image(width,height,rgba){integer(width,1,240,'Width');integer(height,1,240,'Height');
    if(rgba.length!==width*height*4)fail('Invalid RGBA pixel count.');
    const out=new Uint8Array(4+width*height);out.set([74,73,width,height]);
    for(let p=0;p<width*height;p++){const i=p*4,a=rgba[i+3]/255;
      out[p+4]=(Math.round(rgba[i]*a*3/255)<<5)|(Math.round(rgba[i+1]*a*7/255)<<2)|Math.round(rgba[i+2]*a*3/255);}
    return out;
  }
  function decodeImage(data){if(data[0]!==74||data[1]!==73)fail('Images need a JI header.');
    const width=integer(data[2],1,240,'Width'),height=integer(data[3],1,240,'Height');
    if(data.length!==4+width*height||data.slice(4).some(c=>c>127))fail('Invalid palette image pixels.');return {width,height,pixels:data.slice(4)};}
  function tune(events){if(!events.length||events.length>Math.floor((MAX-4)/4))fail('Tune exceeds the disk capacity.');
    const out=new Uint8Array(4+events.length*4);out.set(events.length<=255?[74,77,1,events.length]:[74,77,2,0]);
    events.forEach((e,i)=>{integer(e.frames,0,255,'Duration');integer(e.voice,0,7,'Voice');integer(e.note,0,127,'MIDI note');
      if(![0,128,129,130,131].includes(e.control))fail('Invalid voice control.');out.set([e.frames,e.voice,e.note,e.control],4+i*4);});return out;}
  function decodeTune(data){if(data[0]!==74||data[1]!==77||!((data[2]===1&&data[3]>0&&data.length===4+data[3]*4)||(data[2]===2&&data[3]===0&&data.length>4&&data.length<=MAX&&(data.length-4)%4===0)))fail('Invalid JM note-stream header.');
    const events=[];for(let i=4;i<data.length;i+=4)events.push({frames:data[i],voice:data[i+1],note:data[i+2],control:data[i+3]});tune(events);return events;}
  function notes(text){const events=[];for(const [i,line] of text.trim().split(/\n/).entries()){
    if(!line.trim())continue;const words=line.trim().split(/\s+/);if(words.length!==2)fail(`Tune line ${i+1}: enter MIDI note and frame count.`);
    const note=integer(Number(words[0]),0,127,'Note'),frames=integer(Number(words[1]),1,255,'Frames');
    events.push({frames,voice:0,note,control:129},{frames:0,voice:0,note,control:0});}return tune(events);}
  function midi(data){
    let p=0;const u8=()=>{if(p>=data.length)fail('Truncated MIDI file.');return data[p++];};
    const u16=()=>u8()*256+u8(),u32=()=>u8()*16777216+u8()*65536+u8()*256+u8();
    const tag=()=>String.fromCharCode(u8(),u8(),u8(),u8());
    const vlq=()=>{let n=0;for(let i=0;i<4;i++){const b=u8();n=n*128+(b&127);if(!(b&128))return n;}fail('Invalid MIDI variable-length value.');};
    if(tag()!=='MThd')fail('Select a Standard MIDI File (.mid).');const header=u32();if(header<6)fail('Invalid MIDI header.');
    const format=u16(),tracks=u16(),ppqn=u16();if(format>1||!tracks||tracks>64||!ppqn||(ppqn&32768))fail('Use MIDI format 0 or 1 with a PPQN time division.');
    p+=header-6;const changes=[];let order=0;
    for(let track=0;track<tracks;track++){
      if(tag()!=='MTrk')fail('Missing MIDI track.');const length=u32(),trackEnd=p+length;
      if(trackEnd>data.length)fail('Truncated MIDI track.');let tick=0,running=0;
      while(p<trackEnd){tick+=vlq();let status=u8();if(status<128){if(!running)fail('MIDI running status is missing.');p--;status=running;}
        if(status===255){const kind=u8(),length=vlq();if(p+length>trackEnd)fail('Truncated MIDI meta event.');
          if(kind===81&&length===3){const tempo=data[p]*65536+data[p+1]*256+data[p+2];if(!tempo)fail('Invalid MIDI tempo.');changes.push({tick,tempo,order:order++});}p+=length;if(kind===47)break;
        }else if(status===240||status===247){const length=vlq();p+=length;running=0;
        }else if(status>=128&&status<240){running=status;const command=status>>4,channel=status&15,a=u8(),b=command===12||command===13?0:u8();
          if(a>127||b>127)fail('Invalid MIDI channel data.');if(command===8||command===9)changes.push({tick,channel,note:a,on:command===9&&b>0,order:order++});
        }else fail('Unsupported MIDI event.');if(p>trackEnd)fail('MIDI event exceeds its track.');
      }p=trackEnd;
    }
    changes.sort((a,b)=>a.tick-b.tick||((a.tempo?0:1)-(b.tempo?0:1))||a.order-b.order);
    let lastTick=0,seconds=0,tempo=500000,previousFrame=0;const active=new Map(),events=[];
    const wait=frames=>{if(!events.length&&frames)events.push({frames:0,voice:0,note:60,control:0});
      if(!events.length)return;while(frames>255){const previous=events[events.length-1];previous.frames=255;events.push({...previous,frames:0});frames-=255;}events[events.length-1].frames=frames;};
    for(const change of changes){seconds+=(change.tick-lastTick)*tempo/ppqn/1e6;lastTick=change.tick;
      if(change.tempo){tempo=change.tempo;continue;}const key=change.channel+':'+change.note;
      if(!change.on&&!active.has(key))continue;const frame=Math.round(seconds/.033);wait(Math.max(0,frame-previousFrame));previousFrame=frame;
      if(change.on){let voice=active.get(key);if(voice===undefined){const occupied=new Set(active.values());voice=[0,1,2,3,4,5,6,7].find(v=>!occupied.has(v));if(voice===undefined){
          // Map insertion order tracks note age; a stolen note's later release is ignored.
          const oldest=active.keys().next().value;voice=active.get(oldest);active.delete(oldest);
        }active.set(key,voice);}
        events.push({frames:0,voice,note:change.note,control:change.channel===9?131:129});
      }else{const voice=active.get(key);events.push({frames:0,voice,note:change.note,control:0});active.delete(key);}
    }
    for(const voice of active.values())events.push({frames:0,voice,note:60,control:0});return tune(events);
  }
  return {Disk,SIZE,BASE,MAX,ROOT,FAT,CLUSTERS,BLOCK,types,textBytes,textOf,nameOf,image,decodeImage,tune,decodeTune,notes,midi};
})();

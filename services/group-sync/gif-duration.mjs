// Read GIF timing metadata without decoding image pixels.
export function gifDuration(buffer) {
    if(buffer.toString("ascii",0,3)!=="GIF")throw new Error("Invalid GIF");
    let p=13,ms=0,delay=100,frames=0;
    if(buffer[10]&128)p+=3*(1<<((buffer[10]&7)+1));
    const blocks=()=>{while(p<buffer.length){const n=buffer[p++];if(!n)return;p+=n;}throw new Error("Truncated GIF");};
    while(p<buffer.length) {
        const kind=buffer[p++];
        if(kind===0x3b)break;
        if(kind===0x21) {
            const label=buffer[p++];
            if(label===0xf9){if(buffer[p++]!==4)throw new Error("Invalid GIF control");p++;delay=buffer.readUInt16LE(p)*10;p+=2;p++;if(buffer[p++]!==0)throw new Error("Invalid GIF terminator");}
            else blocks();
        } else if(kind===0x2c) {
            const packed=buffer[p+8];p+=9;
            if(packed&128)p+=3*(1<<((packed&7)+1));
            p++;blocks();ms+=Math.max(20,delay);frames++;
        } else throw new Error("Invalid GIF block");
    }
    if(!frames)throw new Error("Empty GIF");
    return {durationMs:ms,frames};
}

const phases=["01_dawn_loop.gif","02_day_loop.gif","03_dusk_loop.gif","04_night_loop.gif"];
const transitions=["01_dawn_to_day.gif","02_day_to_dusk.gif","03_dusk_to_night.gif","04_night_to_dawn.gif"];
export const phaseAtHour=h=>h>=5&&h<11?0:h>=11&&h<16?1:h>=16&&h<19?2:3;
export const transitionFor=(from,to)=>(from+1)%4===to?transitions[from]:null;
const reduced=matchMedia("(prefers-reduced-motion: reduce)");
class CampScene extends HTMLElement {
    connectedCallback() {
        this.phase=phaseAtHour(new Date().getHours());
        this.innerHTML='<div class="scene-frame"><img alt="같은 차량과 텐트의 시간대별 캠핑 풍경"><canvas role="img" aria-label="캠핑 풍경 정지 화면" hidden></canvas><span class="scene-label"></span></div>';
        this.img=this.querySelector("img");this.canvas=this.querySelector("canvas");
        this.change=()=>this.showLoop();
        this.visibility=()=>{if(!document.hidden){clearTimeout(this.transitionTimer);this.phase=phaseAtHour(new Date().getHours());this.showLoop();}};
        reduced.addEventListener("change",this.change);document.addEventListener("visibilitychange",this.visibility);
        this.showLoop();this.tick=setInterval(()=>this.update(),1000);
    }
    disconnectedCallback(){clearInterval(this.tick);clearTimeout(this.transitionTimer);reduced.removeEventListener("change",this.change);document.removeEventListener("visibilitychange",this.visibility);this.loadId=(this.loadId||0)+1;this.img.removeAttribute("src");}
    get still(){return reduced.matches;}
    load(path) {
        const id=this.loadId=(this.loadId||0)+1;
        return new Promise(resolve=>{
            const next=new Image();next.onload=()=>{
                if(id!==this.loadId||!this.isConnected)return resolve(false);
                this.img.src=next.src;
                this.canvas.width=next.naturalWidth;this.canvas.height=next.naturalHeight;
                this.canvas.getContext("2d").drawImage(next,0,0);
                this.canvas.hidden=!this.still;this.img.hidden=this.still;
                resolve(true);
            };next.onerror=()=>{this.querySelector(".scene-label").textContent="풍경을 불러오지 못했어요";resolve(false);};
            next.src=path;
        });
    }
    showLoop(){
        clearTimeout(this.transitionTimer);
        this.querySelector(".scene-label").textContent=["새벽 · 아침","낮 · 오후","오후 · 해질녘","저녁 · 밤"][this.phase];
        this.load("/camping/loops/"+phases[this.phase]);
    }
    async update(){
        if(document.hidden)return;
        const next=phaseAtHour(new Date().getHours());
        if(next===this.phase)return;
        const file=transitionFor(this.phase,next);this.phase=next;
        if(!file||this.still)return this.showLoop();
        try {
            const response=await fetch("/camping/timing.json");
            if(!response.ok)throw new Error("timing");
            const timing=await response.json();
            if(!this.isConnected)return;
            if(this.still || document.hidden)return this.showLoop();
            if(await this.load("/camping/transitions/"+file))this.transitionTimer=setTimeout(()=>this.showLoop(),timing[file].durationMs);
            else this.showLoop();
        } catch {this.showLoop();}
    }
}
customElements.define("camp-scene",CampScene);

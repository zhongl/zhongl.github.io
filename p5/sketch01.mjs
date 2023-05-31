export default function (p) {

  p.setup = () => {
    let c = p.createCanvas(400, 400);
    let w = c.canvas.parentElement.offsetWidth;
    p.resizeCanvas(w, p.height);
  }

  p.draw = () => {
      p.background(220);
      p.ellipse(50, 50, 80, 80);
  }
}


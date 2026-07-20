import React, { useEffect, useRef } from "react";

interface ConfettiProps {
  active: boolean;
  colors?: string[];
  duration?: number;
  onComplete?: () => void;
}

export const Confetti: React.FC<ConfettiProps> = ({
  active,
  colors = ["#22c55e", "#10b981", "#eab308", "#facc15", "#84cc16", "#fbbf24"], // Green and yellow shades
  duration = 5000,
  onComplete,
}) => {
  const canvasRef = useRef<HTMLCanvasElement>(null);

  useEffect(() => {
    if (!active) return;

    const canvas = canvasRef.current;
    if (!canvas) return;

    const ctx = canvas.getContext("2d");
    if (!ctx) return;

    let animationFrameId: number;
    const startTime = Date.now();

    const resizeCanvas = () => {
      canvas.width = window.innerWidth;
      canvas.height = window.innerHeight;
    };
    resizeCanvas();
    window.addEventListener("resize", resizeCanvas);

    class Particle {
      x: number;
      y: number;
      size: number;
      color: string;
      speedX: number;
      speedY: number;
      rotation: number;
      rotationSpeed: number;
      opacity: number;
      gravity: number;
      friction: number;

      constructor(originX: number, originY: number, angleRange: [number, number]) {
        this.x = originX;
        this.y = originY;
        this.size = Math.random() * 8 + 6;
        this.color = colors[Math.floor(Math.random() * colors.length)];
        
        // Convert angle range (in degrees) to radians and calculate velocity
        const angle = (Math.random() * (angleRange[1] - angleRange[0]) + angleRange[0]) * (Math.PI / 180);
        const speed = Math.random() * 15 + 12; // Initial push speed
        
        this.speedX = Math.cos(angle) * speed;
        this.speedY = Math.sin(angle) * speed;
        
        this.rotation = Math.random() * 360;
        this.rotationSpeed = Math.random() * 6 - 3;
        this.opacity = 1;
        this.gravity = 0.45;
        this.friction = 0.97;
      }

      update() {
        this.speedX *= this.friction;
        this.speedY *= this.friction;
        this.speedY += this.gravity;
        this.x += this.speedX;
        this.y += this.speedY;
        this.rotation += this.rotationSpeed;
        
        // Start fading out after 2.5 seconds
        const elapsed = Date.now() - startTime;
        if (elapsed > duration * 0.5) {
          this.opacity -= 0.015;
        }
      }

      draw() {
        if (!ctx || this.opacity <= 0) return;
        ctx.save();
        ctx.translate(this.x, this.y);
        ctx.rotate((this.rotation * Math.PI) / 180);
        ctx.globalAlpha = this.opacity;
        ctx.fillStyle = this.color;
        
        // Draw confetti rectangle
        ctx.fillRect(-this.size / 2, -this.size / 2, this.size, this.size / 2);
        ctx.restore();
      }
    }

    const particles: Particle[] = [];

    // Create explosions from the bottom-left and bottom-right corners
    const spawnInitialBurst = () => {
      // Left side burst shooting up-right (angles between -70 and -20)
      for (let i = 0; i < 80; i++) {
        particles.push(new Particle(-10, canvas.height * 0.85, [-70, -20]));
      }
      // Right side burst shooting up-left (angles between -160 and -110)
      for (let i = 0; i < 80; i++) {
        particles.push(new Particle(canvas.width + 10, canvas.height * 0.85, [-160, -110]));
      }
      // Center burst shooting upwards
      for (let i = 0; i < 40; i++) {
        particles.push(new Particle(canvas.width / 2, canvas.height + 10, [-110, -70]));
      }
    };

    spawnInitialBurst();

    const animate = () => {
      ctx.clearRect(0, 0, canvas.width, canvas.height);

      // Keep spawning minor trails for the first 800ms
      const elapsed = Date.now() - startTime;
      if (elapsed < 800 && Math.random() < 0.3) {
        particles.push(new Particle(-10, canvas.height * 0.85, [-60, -30]));
        particles.push(new Particle(canvas.width + 10, canvas.height * 0.85, [-150, -120]));
      }

      for (let i = particles.length - 1; i >= 0; i--) {
        const p = particles[i];
        p.update();
        p.draw();

        // Remove if invisible or off-screen
        if (
          p.opacity <= 0 ||
          p.y > canvas.height + 20 ||
          p.x < -50 ||
          p.x > canvas.width + 50
        ) {
          particles.splice(i, 1);
        }
      }

      if (elapsed < duration && particles.length > 0) {
        animationFrameId = requestAnimationFrame(animate);
      } else {
        ctx.clearRect(0, 0, canvas.width, canvas.height);
        if (onComplete) onComplete();
      }
    };

    animate();

    return () => {
      window.removeEventListener("resize", resizeCanvas);
      cancelAnimationFrame(animationFrameId);
    };
  }, [active, colors, duration, onComplete]);

  if (!active) return null;

  return (
    <canvas
      ref={canvasRef}
      className="fixed inset-0 pointer-events-none z-[9999] w-full h-full"
    />
  );
};

#!/usr/bin/env python3
import tkinter as tk
import subprocess
import os

def start_sc():
    subprocess.Popen(['/home/pi/start_sc'])


root = tk.Tk()
root.title("Airkit")
root.geometry("150x80+100+100")
root.resizable(False, False)
root.attributes('-topmost', False)

frame = tk.Frame(root)
frame.pack(fill='both', expand=True, padx=5, pady=5)

start_btn = tk.Button(frame, text="Start SC", command=start_sc, 
                     bg="#4CAF50", fg="white", font=("Arial", 10, "bold"))
start_btn.pack(fill='x', pady=2)

sroot.mainloop()
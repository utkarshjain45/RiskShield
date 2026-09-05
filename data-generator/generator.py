#!/usr/bin/env python
"""
RiskShield AI — Synthetic Payment Transaction Generator Entrypoint
Supports running directly via:
  python generator.py --transactions 100000 --fraud-rate 0.02
or as a module:
  python -m generator --transactions 100000 --fraud-rate 0.02
"""

import sys
import os

# Ensure package path is recognized
current_dir = os.path.dirname(os.path.abspath(__file__))
if current_dir not in sys.path:
    sys.path.insert(0, current_dir)

from generator.__main__ import main

if __name__ == "__main__":
    main()

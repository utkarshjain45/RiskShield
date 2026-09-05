import argparse
import sys
import os

# Add parent directory to sys.path so running from data-generator or root works seamlessly
current_dir = os.path.dirname(os.path.abspath(__file__))
parent_dir = os.path.dirname(current_dir)
if parent_dir not in sys.path:
    sys.path.insert(0, parent_dir)

from generator.config import GeneratorConfig
from generator.engine import GeneratorEngine

def parse_args():
    parser = argparse.ArgumentParser(
        description="RiskShield AI - Synthetic Payment Transaction Generator CLI",
        formatter_class=argparse.ArgumentDefaultsHelpFormatter
    )
    parser.add_argument(
        "-n", "--transactions",
        type=int,
        default=None,
        help="Total number of payment transactions to generate"
    )
    parser.add_argument(
        "-f", "--fraud-rate",
        type=float,
        default=None,
        help="Proportion of transactions marked as fraudulent (e.g. 0.02 for 2%%)"
    )
    parser.add_argument(
        "-s", "--seed",
        type=int,
        default=None,
        help="Deterministic random seed for reproducibility"
    )
    parser.add_argument(
        "-c", "--config",
        type=str,
        default=None,
        help="Path to YAML configuration file"
    )
    parser.add_argument(
        "--merchants",
        type=int,
        default=None,
        help="Unique merchant count to generate"
    )
    parser.add_argument(
        "--customers",
        type=int,
        default=None,
        help="Unique customer count to generate"
    )
    parser.add_argument(
        "--devices",
        type=int,
        default=None,
        help="Unique device count to generate"
    )
    parser.add_argument(
        "--ips",
        type=int,
        default=None,
        help="Unique IP address count to generate"
    )
    parser.add_argument(
        "--output-dir",
        type=str,
        default=None,
        help="Root output directory (defaults to 'data/')"
    )
    return parser.parse_args()

def main():
    args = parse_args()
    
    # Load base config from YAML
    config = GeneratorConfig.load_from_yaml(args.config)
    
    # Apply CLI argument overrides
    if args.transactions is not None:
        config.transactions = args.transactions
    if args.fraud_rate is not None:
        config.fraud_rate = args.fraud_rate
    if args.seed is not None:
        config.random_seed = args.seed
    if args.merchants is not None:
        config.merchants = args.merchants
    if args.customers is not None:
        config.customers = args.customers
    if args.devices is not None:
        config.devices = args.devices
    if args.ips is not None:
        config.ips = args.ips
    if args.output_dir is not None:
        config.output_dirs.raw = os.path.join(args.output_dir, "raw")
        config.output_dirs.processed = os.path.join(args.output_dir, "processed")
        config.output_dirs.stats = os.path.join(args.output_dir, "summary_statistics.json")

    engine = GeneratorEngine(config)
    engine.run()

if __name__ == "__main__":
    main()

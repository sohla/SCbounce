#!/usr/bin/env python3
"""
IMU Data Type Analyzer for Personality Files

Analyzes personality files to categorize their usage of different IMU data types:
1. gyro - gyroscope data (raw, filtered, etc.)
2. accel - accelerometer data (raw, filtered, etc.)  
3. button - button press data
4. both - files using both gyro and accel

Generates a timeline visualization showing patterns in IMU data work.
"""

import os
import re
import json
import glob
import subprocess
from pathlib import Path
from typing import Dict, List, Set, Tuple, Any
from collections import defaultdict, Counter
from datetime import datetime

# Generated data lands here and is gitignored - the scripts are the source of
# truth, not their output. Anchored to this file rather than the working
# directory so it does not matter where the script is invoked from.
OUTPUT_DIR = Path(__file__).resolve().parent / 'output'

class IMUDataAnalyzer:
    def __init__(self, personalities_dir: str):
        self.personalities_dir = Path(personalities_dir)
        self.imu_categories = {
            'gyro': [],
            'accel': [],
            'button': [],
            'both': [],
            'mixed': []
        }
        self.file_analysis = {}
        
    def analyze_all_personalities(self) -> Dict[str, Any]:
        """Analyze all personality files for IMU data usage"""
        print(f"🔍 Analyzing personality files in {self.personalities_dir}")
        
        # Find all .sc files
        sc_files = list(self.personalities_dir.glob("*.sc"))
        print(f"Found {len(sc_files)} personality files")
        
        # Analyze each file
        for sc_file in sc_files:
            try:
                analysis = self.analyze_file_imu_usage(sc_file)
                if analysis:
                    self.file_analysis[analysis['name']] = analysis
                    self.categorize_file(analysis)
            except Exception as e:
                print(f"❌ Error analyzing {sc_file.name}: {e}")
        
        # Get commit timeline data
        timeline_data = self.generate_commit_timeline()
        
        # Generate statistics
        statistics = self.generate_statistics()
        
        return {
            'categories': dict(self.imu_categories),
            'file_analysis': self.file_analysis,
            'timeline': timeline_data,
            'statistics': statistics
        }
    
    def analyze_file_imu_usage(self, file_path: Path) -> Dict[str, Any]:
        """Analyze a single personality file for IMU data patterns"""
        try:
            with open(file_path, 'r', encoding='utf-8') as f:
                content = f.read()
        except UnicodeDecodeError:
            with open(file_path, 'r', encoding='latin-1') as f:
                content = f.read()
        
        name = file_path.stem
        
        analysis = {
            'name': name,
            'file_path': str(file_path),
            'file_size': len(content),
            'gyro_usage': self.extract_gyro_usage(content),
            'accel_usage': self.extract_accel_usage(content),
            'button_usage': self.extract_button_usage(content),
            'sensor_patterns': self.extract_sensor_patterns(content),
            'control_mapping': self.extract_control_mapping(content),
            'filter_usage': self.extract_filter_usage(content),
            'last_modified': self.get_file_last_modified(file_path)
        }
        
        # Determine primary category
        analysis['primary_category'] = self.determine_primary_category(analysis)
        analysis['complexity_score'] = self.calculate_imu_complexity(analysis)
        
        return analysis
    
    def extract_gyro_usage(self, content: str) -> Dict[str, Any]:
        """Extract gyroscope data usage patterns - ONLY count actual usage, not filter assignments"""
        gyro_data = {
            'raw_gyro': [],
            'filtered_gyro': [],
            'gyro_derivatives': [],
            'total_references': 0,
            'mapping_functions': [],
            'parameter_targets': []
        }
        
        # Remove all filter assignment lines first
        lines = content.split('\n')
        content_without_assignments = '\n'.join([
            line for line in lines 
            if not re.search(r'm\.\w*(Attack|Decay)\s*=', line, re.IGNORECASE)
        ])
        
        # Now find actual gyro usage
        usage_patterns = [
            r'm\.gyroX\b',
            r'm\.gyroY\b', 
            r'm\.gyroZ\b',
            r'm\.gyroFiltered\b',
            r'm\.rrate\b'
        ]
        
        all_matches = []
        for pattern in usage_patterns:
            matches = re.findall(pattern, content_without_assignments, re.IGNORECASE)
            all_matches.extend(matches)
            
        # Categorize matches
        for match in all_matches:
            if 'rrate' in match.lower():
                gyro_data['gyro_derivatives'].append(match)
            elif 'filtered' in match.lower():
                gyro_data['filtered_gyro'].append(match)
            else:
                gyro_data['raw_gyro'].append(match)
        
        gyro_data['total_references'] = len(all_matches)
        
        # Only extract context if we have actual usage
        if all_matches:
            for match in set(all_matches):
                # Look for mapping functions in context
                context_matches = re.findall(rf'{re.escape(match)}[^;]*\.(\w*lin\w*)\(', content_without_assignments, re.IGNORECASE)
                gyro_data['mapping_functions'].extend(context_matches)
                
                # Look for parameter targets
                param_matches = re.findall(rf'{re.escape(match)}[^;]*?\\(\w+)', content_without_assignments)
                gyro_data['parameter_targets'].extend(param_matches)
        
        return gyro_data
    
    def extract_accel_usage(self, content: str) -> Dict[str, Any]:
        """Extract accelerometer data usage patterns - ONLY count actual usage, not filter assignments"""
        accel_data = {
            'raw_accel': [],
            'filtered_accel': [],
            'accel_magnitude': [],
            'total_references': 0,
            'mapping_functions': [],
            'parameter_targets': []
        }
        
        # Remove all filter assignment lines first
        lines = content.split('\n')
        content_without_assignments = '\n'.join([
            line for line in lines 
            if not re.search(r'm\.\w*(Attack|Decay)\s*=', line, re.IGNORECASE)
        ])
        
        # Now find actual accel usage
        usage_patterns = [
            r'm\.accelX\b',
            r'm\.accelY\b',
            r'm\.accelZ\b', 
            r'm\.accelFiltered\b',
            r'm\.accelMass\b',
            r'm\.accelMag\b'
        ]
        
        all_matches = []
        for pattern in usage_patterns:
            matches = re.findall(pattern, content_without_assignments, re.IGNORECASE)
            all_matches.extend(matches)
            
        # Categorize matches  
        for match in all_matches:
            if 'mag' in match.lower() or 'mass' in match.lower():
                accel_data['accel_magnitude'].append(match)
            elif 'filtered' in match.lower():
                accel_data['filtered_accel'].append(match)
            else:
                accel_data['raw_accel'].append(match)
        
        accel_data['total_references'] = len(all_matches)
        
        # Only extract context if we have actual usage
        if all_matches:
            for match in set(all_matches):
                # Look for mapping functions
                context_matches = re.findall(rf'{re.escape(match)}[^;]*\.(\w*lin\w*)\(', content_without_assignments, re.IGNORECASE)
                accel_data['mapping_functions'].extend(context_matches)
                
                # Look for parameter targets
                param_matches = re.findall(rf'{re.escape(match)}[^;]*?\\(\w+)', content_without_assignments)
                accel_data['parameter_targets'].extend(param_matches)
        
        return accel_data
    
    def extract_button_usage(self, content: str) -> Dict[str, Any]:
        """Extract button usage patterns"""
        button_data = {
            'button_states': [],
            'button_events': [],
            'button_combinations': [],
            'total_references': 0,
            'control_logic': [],
            'triggered_actions': []
        }
        
        # Find button state references
        button_patterns = [
            r'm\.button\w*',
            r'm\.btn\w*',
            r'm\.switch\w*'
        ]
        for pattern in button_patterns:
            matches = re.findall(pattern, content, re.IGNORECASE)
            if matches:
                button_data['button_states'].extend(matches)
        
        # Find button event logic
        event_patterns = [
            r'if\s*\(\s*m\.button',
            r'if\s*\(\s*m\.btn',
            r'button.*==.*1',
            r'button.*>.*0'
        ]
        for pattern in event_patterns:
            matches = re.findall(pattern, content, re.IGNORECASE)
            if matches:
                button_data['button_events'].extend(matches)
        
        # Find button combinations
        combo_patterns = [
            r'm\.button\w*\s*[&|]\s*m\.button',
            r'm\.btn\w*\s*[&|]\s*m\.btn'
        ]
        for pattern in combo_patterns:
            matches = re.findall(pattern, content, re.IGNORECASE)
            if matches:
                button_data['button_combinations'].extend(matches)
        
        button_data['total_references'] = len(button_data['button_states'] + button_data['button_events'])
        
        # Extract control logic patterns
        if button_data['button_states'] or button_data['button_events']:
            logic_patterns = [
                r'if\s*\([^)]*button[^)]*\)\s*\{[^}]*\}',
                r'case\s*\([^)]*button[^)]*\)',
                r'switch\s*\([^)]*button[^)]*\)'
            ]
            for pattern in logic_patterns:
                matches = re.findall(pattern, content, re.IGNORECASE | re.DOTALL)
                button_data['control_logic'].extend(matches[:3])  # Limit to avoid huge strings
        
        return button_data
    
    def extract_sensor_patterns(self, content: str) -> Dict[str, Any]:
        """Extract overall sensor usage patterns"""
        patterns = {
            'sensor_combinations': [],
            'conditional_logic': [],
            'sensor_math': [],
            'threshold_usage': []
        }
        
        # Find combinations of sensors in same expressions
        combo_patterns = [
            r'm\.(gyro|accel)\w*.*[+\-*/].*m\.(gyro|accel)\w*',
            r'm\.(gyro|accel)\w*.*&&.*m\.button',
            r'm\.button.*&&.*m\.(gyro|accel)\w*'
        ]
        for pattern in combo_patterns:
            matches = re.findall(pattern, content, re.IGNORECASE)
            patterns['sensor_combinations'].extend([f"{m[0]}-{m[1]}" if len(m) > 1 else str(m) for m in matches])
        
        # Find conditional logic with sensors
        if_patterns = [
            r'if\s*\([^)]*m\.(gyro|accel|button)',
            r'case\s*\([^)]*m\.(gyro|accel|button)'
        ]
        for pattern in if_patterns:
            matches = re.findall(pattern, content, re.IGNORECASE)
            patterns['conditional_logic'].extend(matches)
        
        # Find mathematical operations on sensors
        math_patterns = [
            r'm\.(gyro|accel)\w*\s*[+\-*/]\s*[\d.]+',
            r'[\d.]+\s*[+\-*/]\s*m\.(gyro|accel)\w*',
            r'm\.(gyro|accel)\w*\s*[+\-*/]\s*m\.(gyro|accel)\w*'
        ]
        for pattern in math_patterns:
            matches = re.findall(pattern, content, re.IGNORECASE)
            patterns['sensor_math'].extend(matches)
        
        # Find threshold usage
        threshold_patterns = [
            r'm\.(gyro|accel|button)\w*\s*[><]=?\s*[\d.]+',
            r'[\d.]+\s*[><]=?\s*m\.(gyro|accel|button)\w*'
        ]
        for pattern in threshold_patterns:
            matches = re.findall(pattern, content, re.IGNORECASE)
            patterns['threshold_usage'].extend(matches)
        
        return patterns
    
    def extract_control_mapping(self, content: str) -> Dict[str, Any]:
        """Extract how IMU data maps to synthesis parameters"""
        mapping = {
            'direct_mapping': [],
            'scaled_mapping': [],
            'conditional_mapping': [],
            'parameter_types': []
        }
        
        # Find direct parameter mapping
        direct_patterns = [
            r'm\.(gyro|accel|button)\w*[^;]*?\\(\w+)',
        ]
        for pattern in direct_patterns:
            matches = re.findall(pattern, content, re.IGNORECASE)
            mapping['direct_mapping'].extend(matches)
        
        # Find scaled mapping (using linlin, lincurve, etc.)
        scaled_patterns = [
            r'm\.(gyro|accel)\w*\.(\w*lin\w*)\([^)]*\)',
        ]
        for pattern in scaled_patterns:
            matches = re.findall(pattern, content, re.IGNORECASE)
            mapping['scaled_mapping'].extend(matches)
        
        # Find parameter types being controlled
        param_patterns = [
            r'\\(freq|amp|pan|dur|attack|decay|sustain|release)',
            r'\\(cutoff|resonance|delay|reverb)',
            r'\\(rate|pitch|formant|detune)'
        ]
        for pattern in param_patterns:
            matches = re.findall(pattern, content, re.IGNORECASE)
            mapping['parameter_types'].extend(matches)
        
        return mapping
    
    def extract_filter_usage(self, content: str) -> Dict[str, Any]:
        """Extract filtering/smoothing applied to IMU data"""
        filters = {
            'filter_variables': [],
            'filter_operations': [],
            'smoothing_factors': []
        }
        
        # Find filter variable assignments
        filter_patterns = [
            r'm\.(\w*[Ff]ilter\w*)\s*=',
            r'm\.(\w*[Ss]mooth\w*)\s*=',
            r'm\.(\w*[Aa]ttack\w*)\s*=',
            r'm\.(\w*[Dd]ecay\w*)\s*='
        ]
        for pattern in filter_patterns:
            matches = re.findall(pattern, content, re.IGNORECASE)
            filters['filter_variables'].extend(matches)
        
        # Find filter operations
        op_patterns = [
            r'm\.\w+\s*=\s*m\.\w+\s*\*\s*[\d.]+\s*\+\s*\w+\s*\*\s*[\d.]+',  # Simple filter equation
            r'\.lag\s*\([\d.]+\)',  # Lag filtering
            r'\.smooth\s*\([\d.]+\)'  # Smoothing
        ]
        for pattern in op_patterns:
            matches = re.findall(pattern, content, re.IGNORECASE)
            filters['filter_operations'].extend(matches)
        
        return filters
    
    def determine_primary_category(self, analysis: Dict[str, Any]) -> str:
        """Determine the primary IMU data category for this file"""
        gyro_count = analysis['gyro_usage']['total_references']
        accel_count = analysis['accel_usage']['total_references']  
        button_count = analysis['button_usage']['total_references']
        
        # If only one type is used significantly
        if gyro_count > 0 and accel_count == 0 and button_count == 0:
            return 'gyro'
        elif accel_count > 0 and gyro_count == 0 and button_count == 0:
            return 'accel'
        elif button_count > 0 and gyro_count == 0 and accel_count == 0:
            return 'button'
        elif gyro_count > 0 and accel_count > 0 and button_count == 0:
            return 'both'
        elif (gyro_count > 0 or accel_count > 0) and button_count > 0:
            return 'mixed'
        else:
            return 'none'
    
    def calculate_imu_complexity(self, analysis: Dict[str, Any]) -> float:
        """Calculate a complexity score based on IMU usage"""
        score = 0
        
        # Base points for each sensor type
        score += min(analysis['gyro_usage']['total_references'], 10) * 1
        score += min(analysis['accel_usage']['total_references'], 10) * 1
        score += min(analysis['button_usage']['total_references'], 10) * 0.5
        
        # Bonus for using multiple sensor types
        sensor_types = 0
        if analysis['gyro_usage']['total_references'] > 0:
            sensor_types += 1
        if analysis['accel_usage']['total_references'] > 0:
            sensor_types += 1
        if analysis['button_usage']['total_references'] > 0:
            sensor_types += 1
        
        if sensor_types > 1:
            score += sensor_types * 2
        
        # Bonus for filtering/processing
        if analysis['filter_usage']['filter_variables']:
            score += len(analysis['filter_usage']['filter_variables']) * 2
        
        # Bonus for complex mapping
        if analysis['control_mapping']['scaled_mapping']:
            score += len(analysis['control_mapping']['scaled_mapping']) * 1.5
        
        return min(score, 50)  # Cap at 50
    
    def categorize_file(self, analysis: Dict[str, Any]) -> None:
        """Categorize file into appropriate IMU type category"""
        category = analysis['primary_category']
        if category in self.imu_categories:
            self.imu_categories[category].append(analysis['name'])
    
    def get_file_last_modified(self, file_path: Path) -> str:
        """Get the last modified date of the file"""
        try:
            timestamp = file_path.stat().st_mtime
            return datetime.fromtimestamp(timestamp).strftime('%Y-%m-%d')
        except:
            return "unknown"
    
    def generate_commit_timeline(self) -> Dict[str, Any]:
        """Generate timeline data from git commits"""
        timeline_data = {
            'commits': [],
            'daily_activity': defaultdict(lambda: defaultdict(int)),
            'monthly_activity': defaultdict(lambda: defaultdict(int))
        }
        
        try:
            # Get git log for personality files
            cmd = [
                'git', 'log', 
                '--format=%h|%ad|%s|%f',
                '--date=short',
                '--all',
                '--',
                'personalities/*'
            ]
            result = subprocess.run(cmd, capture_output=True, text=True, cwd=self.personalities_dir.parent)
            
            if result.returncode == 0:
                for line in result.stdout.strip().split('\n'):
                    if line:
                        parts = line.split('|', 3)
                        if len(parts) >= 4:
                            commit_hash, date, message, filename = parts
                            
                            # Try to categorize based on commit message
                            imu_type = self.categorize_commit_message(message)
                            
                            commit_data = {
                                'hash': commit_hash,
                                'date': date,
                                'message': message,
                                'filename': filename,
                                'imu_type': imu_type
                            }
                            
                            timeline_data['commits'].append(commit_data)
                            
                            # Aggregate daily/monthly data
                            timeline_data['daily_activity'][date][imu_type] += 1
                            month_key = date[:7]  # YYYY-MM
                            timeline_data['monthly_activity'][month_key][imu_type] += 1
            
        except Exception as e:
            print(f"Warning: Could not generate timeline data: {e}")
        
        return timeline_data
    
    def categorize_commit_message(self, message: str) -> str:
        """Categorize commit message by likely IMU data type"""
        message_lower = message.lower()
        
        # Look for keywords in commit messages
        if any(word in message_lower for word in ['gyro', 'rotation', 'spin', 'turn', 'orient']):
            return 'gyro'
        elif any(word in message_lower for word in ['accel', 'acceleration', 'movement', 'motion', 'shake']):
            return 'accel'  
        elif any(word in message_lower for word in ['button', 'press', 'click', 'tap', 'trigger']):
            return 'button'
        elif any(word in message_lower for word in ['sensor', 'imu', 'both', 'combined']):
            return 'mixed'
        else:
            return 'general'
    
    def generate_statistics(self) -> Dict[str, Any]:
        """Generate overall statistics"""
        stats = {
            'total_files': len(self.file_analysis),
            'category_counts': {cat: len(files) for cat, files in self.imu_categories.items()},
            'complexity_distribution': {},
            'sensor_usage_stats': {
                'gyro_users': 0,
                'accel_users': 0, 
                'button_users': 0,
                'multi_sensor_users': 0
            },
            'top_complexity_files': [],
            'filter_usage_stats': {}
        }
        
        complexities = []
        filter_counts = defaultdict(int)
        
        for analysis in self.file_analysis.values():
            complexities.append(analysis['complexity_score'])
            
            # Count sensor usage
            if analysis['gyro_usage']['total_references'] > 0:
                stats['sensor_usage_stats']['gyro_users'] += 1
            if analysis['accel_usage']['total_references'] > 0:
                stats['sensor_usage_stats']['accel_users'] += 1
            if analysis['button_usage']['total_references'] > 0:
                stats['sensor_usage_stats']['button_users'] += 1
            
            sensor_count = sum([
                1 if analysis['gyro_usage']['total_references'] > 0 else 0,
                1 if analysis['accel_usage']['total_references'] > 0 else 0,
                1 if analysis['button_usage']['total_references'] > 0 else 0
            ])
            if sensor_count > 1:
                stats['sensor_usage_stats']['multi_sensor_users'] += 1
            
            # Count filter usage
            for filter_var in analysis['filter_usage']['filter_variables']:
                filter_counts[filter_var] += 1
        
        # Complexity distribution
        if complexities:
            stats['complexity_distribution'] = {
                'min': min(complexities),
                'max': max(complexities),
                'avg': sum(complexities) / len(complexities),
                'median': sorted(complexities)[len(complexities)//2]
            }
            
            # Top complexity files
            sorted_by_complexity = sorted(
                self.file_analysis.items(),
                key=lambda x: x[1]['complexity_score'],
                reverse=True
            )
            stats['top_complexity_files'] = [
                {'name': name, 'complexity': data['complexity_score'], 'category': data['primary_category']}
                for name, data in sorted_by_complexity[:10]
            ]
        
        stats['filter_usage_stats'] = dict(filter_counts)
        
        return stats


def main():
    # Configuration
    personalities_dir = "../personalities"
    OUTPUT_DIR.mkdir(parents=True, exist_ok=True)
    output_file = OUTPUT_DIR / "imu_type_analysis.json"
    
    # Initialize analyzer
    analyzer = IMUDataAnalyzer(personalities_dir)
    
    # Run analysis
    print("🚀 Starting IMU data type analysis...")
    results = analyzer.analyze_all_personalities()
    
    # Save results
    print(f"💾 Saving results to {output_file}")
    with open(output_file, 'w') as f:
        json.dump(results, f, indent=2, default=str)
    
    # Print summary
    print(f"✅ Analysis complete!")
    print(f"📊 Analyzed {results['statistics']['total_files']} personality files")
    
    print("\n📈 IMU Data Type Distribution:")
    for category, count in results['statistics']['category_counts'].items():
        if count > 0:
            print(f"  • {category}: {count} files")
    
    print(f"\n🎛️ Sensor Usage:")
    sensor_stats = results['statistics']['sensor_usage_stats']
    print(f"  • Gyro users: {sensor_stats['gyro_users']}")
    print(f"  • Accel users: {sensor_stats['accel_users']}")  
    print(f"  • Button users: {sensor_stats['button_users']}")
    print(f"  • Multi-sensor users: {sensor_stats['multi_sensor_users']}")
    
    print(f"\n🏆 Top Complex Files:")
    for file_info in results['statistics']['top_complexity_files'][:5]:
        print(f"  • {file_info['name']}: {file_info['complexity']:.1f} ({file_info['category']})")


if __name__ == "__main__":
    main()
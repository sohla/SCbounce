#!/usr/bin/env python3
"""
Commit IMU Mapping Analyzer

Analyzes git commits to see when specific IMU mappings were changed.
For each commit, checks the diff to see if lines containing IMU mappings
(gyro, accel, rotation rate) were modified.

Categories:
- gyro: Changes to m.gyroX/Y/Z/Filtered mappings  
- accel: Changes to m.accelX/Y/Z/Mass/Filtered mappings
- rrate: Changes to m.rrate/rrateMassFiltered mappings (rotation rate)
- button: Changes to m.button mappings
- mixed: Commits changing multiple sensor types
"""

import os
import re
import json
import subprocess
from datetime import datetime
from typing import Dict, List, Set, Tuple, Any
from collections import defaultdict
from pathlib import Path

# Generated data lands here and is gitignored - the scripts are the source of
# truth, not their output. Anchored to this file rather than the working
# directory so it does not matter where the script is invoked from.
OUTPUT_DIR = Path(__file__).resolve().parent / 'output'

# Commit scope. Must stay identical across every analyzer in this directory,
# or the timelines are drawn from different populations and cannot be read
# against each other. This script used a relative window ("2 years ago"),
# which slid forward on every run, and was missing --all.
SINCE = '2024-01-01'

class CommitIMUAnalyzer:
    def __init__(self, repo_path: str = "../"):
        self.repo_path = repo_path
        self.commits = []

    def analyze_commits(self, since_date: str = SINCE) -> Dict[str, Any]:
        """Analyze commits for IMU mapping changes"""
        print(f"🔍 Analyzing commits since {since_date}...")
        
        # Get commit list for personality files
        commits = self.get_personality_commits(since_date)
        print(f"Found {len(commits)} personality-related commits")
        
        # Analyze each commit's diff
        analyzed_commits = []
        for commit in commits:
            commit_analysis = self.analyze_commit_diff(commit)
            if commit_analysis:
                analyzed_commits.append(commit_analysis)
        
        print(f"✅ Analyzed {len(analyzed_commits)} commits with IMU changes")
        
        # Generate timeline data
        timeline_data = self.generate_timeline_data(analyzed_commits)
        
        # Generate statistics
        statistics = self.generate_statistics(analyzed_commits)
        
        return {
            'commits': analyzed_commits,
            'timeline': timeline_data,
            'statistics': statistics
        }
    
    def get_personality_commits(self, since_date: str) -> List[Dict[str, str]]:
        """Get all commits that touched personality files"""
        try:
            # Get commit log for personality files
            cmd = [
                'git', 'log',
                '--all',
                f'--since={since_date}',
                '--format=%H|%ad|%s|%an',
                '--date=iso',
                '--',
                'personalities/'
            ]
            
            result = subprocess.run(cmd, capture_output=True, text=True, cwd=self.repo_path)
            
            commits = []
            if result.returncode == 0:
                for line in result.stdout.strip().split('\n'):
                    if line:
                        parts = line.split('|', 3)
                        if len(parts) >= 4:
                            hash_val, date_str, message, author = parts
                            commits.append({
                                'hash': hash_val,
                                'date': date_str,
                                'message': message.strip(),
                                'author': author.strip()
                            })
            
            return commits
            
        except Exception as e:
            print(f"Error getting commits: {e}")
            return []
    
    def analyze_commit_diff(self, commit: Dict[str, str]) -> Dict[str, Any]:
        """Analyze the diff of a specific commit for IMU mapping changes"""
        try:
            # Get diff for this commit (only personality files)
            cmd = [
                'git', 'show', 
                '--format=',  # Don't show commit message
                '--name-only',
                commit['hash'],
                '--',
                'personalities/'
            ]
            
            files_result = subprocess.run(cmd, capture_output=True, text=True, cwd=self.repo_path)
            changed_files = [f for f in files_result.stdout.strip().split('\n') if f]
            
            # Get the actual diff content
            diff_cmd = [
                'git', 'show',
                '--format=',
                commit['hash'],
                '--',
                'personalities/'
            ]
            
            diff_result = subprocess.run(diff_cmd, capture_output=True, text=True, cwd=self.repo_path)
            diff_content = diff_result.stdout
            
            # Analyze the diff for IMU mapping changes
            imu_changes = self.extract_imu_changes_from_diff(diff_content)
            
            if not imu_changes['has_imu_changes']:
                return None
            
            # Determine primary category
            primary_category = self.determine_commit_category(imu_changes)
            
            return {
                'hash': commit['hash'],
                'date': commit['date'],
                'message': commit['message'],
                'author': commit['author'],
                'changed_files': changed_files,
                'imu_changes': imu_changes,
                'primary_category': primary_category,
                'change_magnitude': self.calculate_change_magnitude(imu_changes)
            }
            
        except Exception as e:
            print(f"Error analyzing commit {commit['hash']}: {e}")
            return None
    
    def extract_imu_changes_from_diff(self, diff_content: str) -> Dict[str, Any]:
        """Extract IMU mapping changes from git diff content"""
        changes = {
            'gyro_changes': [],
            'accel_changes': [],
            'rrate_changes': [],
            'button_changes': [],
            'has_imu_changes': False,
            'total_imu_lines': 0
        }
        
        # Look for added/modified lines (starting with +) that contain IMU mappings
        lines = diff_content.split('\n')
        
        for line in lines:
            # Skip lines that are not additions/modifications
            if not line.startswith('+') or line.startswith('+++'):
                continue
            
            # Remove the + prefix
            code_line = line[1:].strip()
            
            # Skip empty lines and comments
            if not code_line or code_line.startswith('//'):
                continue
            
            # Check for different types of IMU mappings
            imu_change = self.classify_imu_line(code_line)
            if imu_change:
                changes[f"{imu_change['type']}_changes"].append({
                    'line': code_line,
                    'sensor': imu_change['sensor'],
                    'mapping_function': imu_change['mapping_function'],
                    'parameters': imu_change['parameters']
                })
                changes['has_imu_changes'] = True
                changes['total_imu_lines'] += 1
        
        return changes
    
    def classify_imu_line(self, line: str) -> Dict[str, Any]:
        """Classify a line of code to determine if it's an IMU mapping"""
        
        # Gyro patterns
        gyro_patterns = [
            r'm\.gyro[XYZ](?:Filtered)?',
            r'm\.gyroFiltered'
        ]
        
        # Accelerometer patterns  
        accel_patterns = [
            r'm\.accel[XYZ](?:Filtered)?',
            r'm\.accelMass(?:Filtered)?',
            r'm\.accelFiltered'
        ]
        
        # Rotation rate patterns
        rrate_patterns = [
            r'm\.rrate(?:MassFiltered|Filtered)?',
            r'm\.rrateMassFiltered'
        ]
        
        # Button patterns
        button_patterns = [
            r'd\.sensors\.digiInEvent\[\d+\]',
            r'd\.sensors\.digiInEvent',
            r'm\.button\w*'  # Keep old pattern just in case
        ]
        
        # Check for mapping functions
        mapping_functions = ['linlin', 'lincurve', 'clip', 'explin', 'expexplin', 'lag']
        
        # Look for sensor usage with mapping
        for sensor_type, patterns in [
            ('gyro', gyro_patterns),
            ('accel', accel_patterns), 
            ('rrate', rrate_patterns),
            ('button', button_patterns)
        ]:
            for pattern in patterns:
                if re.search(pattern, line, re.IGNORECASE):
                    # For buttons, also check for conditional logic (if statements, comparisons)
                    if sensor_type == 'button':
                        # Check for button usage in conditionals or assignments
                        if any(keyword in line.lower() for keyword in ['if', '==', '!=', '>', '<', '=', 'case']):
                            sensor_match = re.search(pattern, line, re.IGNORECASE)
                            sensor = sensor_match.group(0) if sensor_match else 'unknown'
                            
                            return {
                                'type': sensor_type,
                                'sensor': sensor,
                                'mapping_function': 'conditional',
                                'parameters': 'button_logic',
                                'full_line': line
                            }
                    
                    # Check if this line contains actual mapping (not just assignment)
                    for mapping_func in mapping_functions:
                        if mapping_func in line.lower():
                            # Extract the sensor variable
                            sensor_match = re.search(pattern, line, re.IGNORECASE)
                            sensor = sensor_match.group(0) if sensor_match else 'unknown'
                            
                            # Extract mapping function and parameters
                            func_pattern = rf'\.({mapping_func})\s*\(([^)]*)\)'
                            func_match = re.search(func_pattern, line, re.IGNORECASE)
                            
                            return {
                                'type': sensor_type,
                                'sensor': sensor,
                                'mapping_function': mapping_func,
                                'parameters': func_match.group(2) if func_match else '',
                                'full_line': line
                            }
        
        return None
    
    def determine_commit_category(self, imu_changes: Dict[str, Any]) -> str:
        """Determine the primary category for this commit"""
        change_counts = {
            'gyro': len(imu_changes['gyro_changes']),
            'accel': len(imu_changes['accel_changes']),
            'rrate': len(imu_changes['rrate_changes']),
            'button': len(imu_changes['button_changes'])
        }
        
        # Count non-zero categories
        active_categories = [cat for cat, count in change_counts.items() if count > 0]
        
        if len(active_categories) == 0:
            return 'none'
        elif len(active_categories) == 1:
            return active_categories[0]
        elif len(active_categories) > 1:
            return 'mixed'
        else:
            # Return the category with most changes
            return max(change_counts, key=change_counts.get)
    
    def calculate_change_magnitude(self, imu_changes: Dict[str, Any]) -> float:
        """Calculate a magnitude score for the changes"""
        total_lines = imu_changes['total_imu_lines']
        
        # Weight different types of changes
        weights = {'gyro': 1.0, 'accel': 1.0, 'rrate': 1.2, 'button': 0.8}
        
        magnitude = 0
        for change_type in ['gyro', 'accel', 'rrate', 'button']:
            changes = imu_changes[f'{change_type}_changes']
            magnitude += len(changes) * weights[change_type]
        
        return magnitude
    
    def generate_timeline_data(self, commits: List[Dict[str, Any]]) -> Dict[str, Any]:
        """Generate timeline data for visualization"""
        
        # Group commits by date (day level)
        daily_activity = defaultdict(lambda: defaultdict(int))
        monthly_activity = defaultdict(lambda: defaultdict(int))
        
        for commit in commits:
            date_obj = datetime.fromisoformat(commit['date'].replace('Z', '+00:00'))
            date_key = date_obj.strftime('%Y-%m-%d')
            month_key = date_obj.strftime('%Y-%m')
            
            category = commit['primary_category']
            magnitude = commit['change_magnitude']
            
            daily_activity[date_key][category] += magnitude
            monthly_activity[month_key][category] += magnitude
        
        # Create timeline entries
        timeline_entries = []
        for commit in commits:
            date_obj = datetime.fromisoformat(commit['date'].replace('Z', '+00:00'))
            
            timeline_entries.append({
                'date': date_obj.isoformat(),
                'category': commit['primary_category'],
                'magnitude': commit['change_magnitude'],
                'message': commit['message'],
                'hash': commit['hash'][:8],
                'author': commit['author'],
                'files': len(commit['changed_files']),
                'details': {
                    'gyro_changes': len(commit['imu_changes']['gyro_changes']),
                    'accel_changes': len(commit['imu_changes']['accel_changes']),
                    'rrate_changes': len(commit['imu_changes']['rrate_changes']),
                    'button_changes': len(commit['imu_changes']['button_changes'])
                }
            })
        
        return {
            'entries': sorted(timeline_entries, key=lambda x: x['date']),
            'daily_activity': dict(daily_activity),
            'monthly_activity': dict(monthly_activity)
        }
    
    def generate_statistics(self, commits: List[Dict[str, Any]]) -> Dict[str, Any]:
        """Generate statistics about IMU mapping changes"""
        
        total_commits = len(commits)
        categories = defaultdict(int)
        authors = defaultdict(int)
        total_magnitude = 0
        
        # Count by category and author
        for commit in commits:
            categories[commit['primary_category']] += 1
            authors[commit['author']] += 1
            total_magnitude += commit['change_magnitude']
        
        # Find most active periods
        monthly_activity = defaultdict(int)
        for commit in commits:
            date_obj = datetime.fromisoformat(commit['date'].replace('Z', '+00:00'))
            month_key = date_obj.strftime('%Y-%m')
            monthly_activity[month_key] += commit['change_magnitude']
        
        most_active_month = max(monthly_activity.items(), key=lambda x: x[1]) if monthly_activity else ('N/A', 0)
        
        # Calculate change frequency by type
        total_changes = defaultdict(int)
        for commit in commits:
            for change_type in ['gyro', 'accel', 'rrate', 'button']:
                total_changes[change_type] += len(commit['imu_changes'][f'{change_type}_changes'])
        
        return {
            'total_commits': total_commits,
            'category_distribution': dict(categories),
            'author_distribution': dict(authors),
            'total_magnitude': total_magnitude,
            'average_magnitude': total_magnitude / total_commits if total_commits > 0 else 0,
            'most_active_month': most_active_month,
            'change_type_totals': dict(total_changes),
            'date_range': {
                'start': min(commit['date'] for commit in commits) if commits else None,
                'end': max(commit['date'] for commit in commits) if commits else None
            }
        }


def main():
    # Configuration
    OUTPUT_DIR.mkdir(parents=True, exist_ok=True)
    output_file = OUTPUT_DIR / "commit_imu_analysis.json"
    
    # Initialize analyzer
    analyzer = CommitIMUAnalyzer()
    
    # Run analysis
    print("🚀 Starting commit IMU mapping analysis...")
    results = analyzer.analyze_commits()
    
    # Save results
    print(f"💾 Saving results to {output_file}")
    with open(output_file, 'w') as f:
        json.dump(results, f, indent=2, default=str)
    
    print(f"✅ Analysis complete!")
    print(f"📊 Analyzed {results['statistics']['total_commits']} commits with IMU mapping changes")
    
    # Print summary statistics
    print("\n📈 IMU Mapping Change Distribution:")
    for category, count in results['statistics']['category_distribution'].items():
        print(f"  • {category}: {count} commits")
    
    print(f"\n🏆 Change Type Totals:")
    for change_type, total in results['statistics']['change_type_totals'].items():
        print(f"  • {change_type}: {total} mapping changes")
    
    print(f"\n📅 Most Active Month: {results['statistics']['most_active_month'][0]} ({results['statistics']['most_active_month'][1]:.1f} total magnitude)")


if __name__ == "__main__":
    main()
#!/usr/bin/env python3
import subprocess
import re
import json
from collections import defaultdict, Counter
from datetime import datetime

def get_all_commits():
    """Extract all commits with their metadata."""
    result = subprocess.run(
        ['git', 'log', '--all', '--since=2024-01-01', '--pretty=format:%H|%ai|%an|%s|%b'],
        capture_output=True,
        text=True
    )
    
    commits = []
    for line in result.stdout.strip().split('\n'):
        if not line or '|' not in line:
            continue
            
        parts = line.split('|', 4)
        if len(parts) >= 4:
            commit = {
                'hash': parts[0],
                'date': parts[1],
                'author': parts[2],
                'subject': parts[3],
                'body': parts[4] if len(parts) > 4 else ''
            }
            commits.append(commit)
    
    return commits

def analyze_word_frequency(commits):
    """Build word frequency data with commit associations."""
    
    # Stopwords to exclude
    stopwords = {
        'the', 'a', 'an', 'and', 'or', 'but', 'in', 'on', 'at', 'to', 'for',
        'of', 'with', 'by', 'from', 'up', 'about', 'into', 'through', 'during',
        'i', 'me', 'my', 'we', 'our', 'you', 'your', 'it', 'its', 'they', 'them',
        'this', 'that', 'these', 'those', 'is', 'are', 'was', 'were', 'be', 'been',
        'have', 'has', 'had', 'do', 'does', 'did', 'will', 'would', 'should',
        'can', 'could', 'may', 'might', 'must', 'shall',
        'github','sohla','com','https','http','www','scbounce','oscmusicmachine'
    }
    
    # Track which commits contain which words
    word_to_commits = defaultdict(list)
    word_counts = Counter()
    
    for commit in commits:
        # Combine subject and body
        text = commit['subject'] + ' ' + commit['body']
        
        # Extract words
        words = re.findall(r'\b[a-z]{3,}\b', text.lower())
        
        # Filter and count
        unique_words_in_commit = set()
        for word in words:
            if word not in stopwords:
                unique_words_in_commit.add(word)
        
        # Associate commits with words
        for word in unique_words_in_commit:
            word_counts[word] += 1
            word_to_commits[word].append({
                'hash': commit['hash'][:8],
                'date': commit['date'][:10],
                'datetime': commit['date'],  # Full datetime for timeline
                'author': commit['author'],
                'subject': commit['subject']
            })
    
    return word_counts, word_to_commits

def generate_json_data(word_counts, word_to_commits, top_n=200):
    """Generate JSON data for visualization."""
    
    # Get top words
    top_words = word_counts.most_common(top_n)
    
    # Calculate date range
    all_dates = []
    for commits in word_to_commits.values():
        for commit in commits:
            try:
                date_obj = datetime.fromisoformat(commit['datetime'].replace('Z', '+00:00'))
                all_dates.append(date_obj)
            except:
                continue
    
    min_date = min(all_dates) if all_dates else datetime.now()
    max_date = max(all_dates) if all_dates else datetime.now()
    
    # Build data structure
    data = {
        'words': [],
        'commits': {},
        'timeline': {
            'min_date': min_date.isoformat(),
            'max_date': max_date.isoformat()
        }
    }
    
    for word, count in top_words:
        data['words'].append({
            'text': word,
            'size': count
        })
        data['commits'][word] = word_to_commits[word]
    
    return data

def main():
    print("Analyzing git repository...")
    
    # Get commits
    commits = get_all_commits()
    print(f"Found {len(commits)} commits")
    
    # Analyze word frequency
    word_counts, word_to_commits = analyze_word_frequency(commits)
    print(f"Found {len(word_counts)} unique words")
    
    # Generate JSON
    data = generate_json_data(word_counts, word_to_commits, top_n=200)
    
    # Save to file
    with open('commits_data.json', 'w') as f:
        json.dump(data, f, indent=2)
    
    print("commits.json")

if __name__ == '__main__':
    main()
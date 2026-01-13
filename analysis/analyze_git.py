#!/usr/bin/env python3
import subprocess
import re
import json
from collections import defaultdict, Counter
from datetime import datetime

def get_commit_files(commit_hash):
    """Get list of files changed in a commit."""
    result = subprocess.run(
        ['git', 'show', '--name-only', '--pretty=format:', commit_hash],
        capture_output=True,
        text=True
    )
    files = [f.strip() for f in result.stdout.strip().split('\n') if f.strip()]
    return files

def categorize_commit(files):
    """Categorize commit based on files changed."""
    if not files:
        return 'other'

    personality_patterns = ['personalities/', 'lists/', 'personalityController']
    code_patterns = ['code2.0/', 'code3.0/']

    has_personality = any(any(pattern in f for pattern in personality_patterns) for f in files)
    has_code = any(any(pattern in f for pattern in code_patterns) for f in files)

    # If it has both, check the ratio
    if has_personality and has_code:
        personality_count = sum(1 for f in files if any(pattern in f for pattern in personality_patterns))
        code_count = sum(1 for f in files if any(pattern in f for pattern in code_patterns))

        # Calculate ratio (smaller / larger) to get a value between 0 and 1
        ratio = min(personality_count, code_count) / max(personality_count, code_count)

        # If ratio is >= 0.5, consider it mixed (e.g., 1:2, 2:3, 3:4 are mixed)
        if ratio >= 0.5:
            return 'mixed'
        else:
            return 'personality' if personality_count > code_count else 'implementation'
    elif has_personality:
        return 'personality'
    elif has_code:
        return 'implementation'
    else:
        return 'other'

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
            commit_hash = parts[0]
            files = get_commit_files(commit_hash)
            commit_type = categorize_commit(files)

            commit = {
                'hash': commit_hash,
                'date': parts[1],
                'author': parts[2],
                'subject': parts[3],
                'body': parts[4] if len(parts) > 4 else '',
                'files': files,
                'type': commit_type
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
                'subject': commit['subject'],
                'files': commit.get('files', []),
                'type': commit.get('type', 'other')
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
#!/usr/bin/env python3
import subprocess
import re
import json
from collections import defaultdict, Counter
from datetime import datetime
from pathlib import Path

# Generated data lands here and is gitignored - the scripts are the source of
# truth, not their output. Anchored to this file rather than the working
# directory so it does not matter where the script is invoked from.
OUTPUT_DIR = Path(__file__).resolve().parent / 'output'

# Commit scope. Must stay identical across every analyzer in this directory,
# or the timelines are drawn from different populations and cannot be read
# against each other.
GIT_SCOPE = ['--all', '--since=2024-01-01']

# Field and record separators for `git log --pretty`. A commit body spans
# multiple lines and may itself contain '|', so neither a newline nor a pipe
# can delimit anything. Splitting on them truncated every multi-line body to
# its first line, and turned one body line that happened to contain pipes
# ("/airkit/state now lists idle|tuning|piece|curtain") into a phantom commit
# whose "hash" was then handed to `git show`.
#
# The format string uses git's %x00/%x01 placeholders rather than the bytes
# themselves: a literal NUL cannot be passed through argv (subprocess raises
# "embedded null byte"), so git emits the separators and only the parsing side
# below splits on the real characters.
FIELD_FMT, FIELD_SEP = '%x00', '\x00'
RECORD_FMT, RECORD_SEP = '%x01', '\x01'

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
    fmt = FIELD_FMT.join(['%H', '%ai', '%an', '%s', '%b']) + RECORD_FMT
    result = subprocess.run(
        ['git', 'log', *GIT_SCOPE, '--pretty=format:' + fmt],
        capture_output=True,
        text=True
    )

    commits = []
    for record in result.stdout.split(RECORD_SEP):
        # `format:` joins records with a newline, so every record after the
        # first arrives with one leading.
        record = record.lstrip('\n')
        if not record.strip():
            continue

        parts = record.split(FIELD_SEP)
        if len(parts) < 5:
            continue

        commit_hash = parts[0]
        files = get_commit_files(commit_hash)

        commits.append({
            'hash': commit_hash,
            'date': parts[1],
            'author': parts[2],
            'subject': parts[3],
            'body': parts[4],
            'files': files,
            'type': categorize_commit(files)
        })

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
    OUTPUT_DIR.mkdir(parents=True, exist_ok=True)
    output_file = OUTPUT_DIR / 'commits_data.json'
    with open(output_file, 'w') as f:
        json.dump(data, f, indent=2)

    print(f"Wrote {output_file}")

if __name__ == '__main__':
    main()
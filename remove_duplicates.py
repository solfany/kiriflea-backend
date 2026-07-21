import os
import re
import glob

def clean_duplicate_comments(file_path):
    with open(file_path, 'r', encoding='utf-8') as f:
        lines = f.readlines()

    new_lines = []
    i = 0
    while i < len(lines):
        line = lines[i]
        new_lines.append(line)
        if "@org.hibernate.annotations.Comment" in line:
            # Check following lines and skip if they are also @Comment
            j = i + 1
            while j < len(lines) and "@org.hibernate.annotations.Comment" in lines[j]:
                j += 1
            i = j - 1 # skip duplicates
        i += 1

    with open(file_path, 'w', encoding='utf-8') as f:
        f.writelines(new_lines)

entity_files = glob.glob("/Users/solfany/Project/kiriflea-market/kiriflea-backend/src/main/java/com/nplohs/market/**/entity/*.java", recursive=True)
for f in entity_files:
    clean_duplicate_comments(f)
print(f"Cleaned duplicates in {len(entity_files)} entity files.")

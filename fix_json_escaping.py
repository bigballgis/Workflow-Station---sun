#!/usr/bin/env python3
"""
Fix JSON escaping issues in INSERT statements
"""

import re
import sys
import json

def fix_json_escaping(input_file, output_file):
    """
    Fix over-escaped JSON strings in INSERT statements
    """
    with open(input_file, 'r', encoding='utf-8') as f:
        content = f.read()
    
    # Pattern to match config_json values with over-escaped quotes
    # Matches: '{"key": "{\\\\"nested\\\\"}"}'
    pattern = re.compile(r"'(\{[^']*\\\\\\\\[^']*\})'")
    
    fixes = 0
    
    def fix_escape(match):
        nonlocal fixes
        json_str = match.group(1)
        
        # Replace quadruple backslashes with double
        # \\\\ -> \\
        fixed = json_str.replace('\\\\\\\\', '\\\\')
        
        # Also fix triple backslashes if any
        # \\\" -> \"
        fixed = fixed.replace('\\\\\"', '\\\"')
        
        fixes += 1
        return f"'{fixed}'"
    
    # Apply fixes
    fixed_content = pattern.sub(fix_escape, content)
    
    # Write output
    with open(output_file, 'w', encoding='utf-8') as f:
        f.write(fixed_content)
    
    print(f"✅ Fixed {fixes} JSON escaping issues")
    print(f"   Input:  {input_file}")
    print(f"   Output: {output_file}")
    
    return fixes

if __name__ == '__main__':
    if len(sys.argv) != 3:
        print("Usage: python fix_json_escaping.py <input_file> <output_file>")
        print("Example: python fix_json_escaping.py workflow_platform_v2_insert_fixed.sql workflow_platform_v2_final.sql")
        sys.exit(1)
    
    input_file = sys.argv[1]
    output_file = sys.argv[2]
    
    try:
        fixes = fix_json_escaping(input_file, output_file)
        if fixes > 0:
            print(f"\n✅ All done! Use {output_file} in DBeaver.")
        else:
            print(f"\n⚠️  No JSON escaping issues found.")
    except Exception as e:
        print(f"❌ Error: {e}")
        import traceback
        traceback.print_exc()
        sys.exit(1)

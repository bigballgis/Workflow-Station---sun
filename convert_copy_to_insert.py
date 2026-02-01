#!/usr/bin/env python3
"""
Convert PostgreSQL COPY FROM stdin commands to INSERT statements
"""

import re
import sys

def convert_copy_to_insert(input_file, output_file):
    """
    Convert COPY FROM stdin commands in a PostgreSQL dump file to INSERT statements
    """
    with open(input_file, 'r', encoding='utf-8') as f:
        content = f.read()
    
    # Pattern to match COPY command
    # COPY table_name (columns) FROM stdin;
    copy_pattern = re.compile(
        r'COPY\s+([\w.]+)\s*\((.*?)\)\s+FROM\s+stdin;(.*?)\\\.',
        re.DOTALL | re.MULTILINE
    )
    
    def replace_copy(match):
        table_name = match.group(1)
        columns = match.group(2)
        data_block = match.group(3).strip()
        
        # If no data, return empty comment
        if not data_block:
            return f"-- No data for table {table_name}\n"
        
        # Split data into lines
        lines = [line for line in data_block.split('\n') if line.strip()]
        
        if not lines:
            return f"-- No data for table {table_name}\n"
        
        # Build INSERT statements
        result = []
        result.append(f"--")
        result.append(f"-- Data for Name: {table_name}; Type: TABLE DATA; Schema: public; Owner: platform")
        result.append(f"--\n")
        
        # Process each line
        insert_values = []
        for line in lines:
            # Split by tab
            values = line.split('\t')
            
            # Convert values to SQL format
            sql_values = []
            for value in values:
                if value == '\\N':
                    # NULL value
                    sql_values.append('NULL')
                elif value == 't':
                    # Boolean true
                    sql_values.append('true')
                elif value == 'f':
                    # Boolean false
                    sql_values.append('false')
                else:
                    # Escape single quotes and wrap in quotes
                    escaped_value = value.replace("'", "''")
                    sql_values.append(f"'{escaped_value}'")
            
            insert_values.append(f"({', '.join(sql_values)})")
        
        # Build INSERT statement
        # Split into multiple INSERT statements if too many rows (for better readability)
        batch_size = 100
        for i in range(0, len(insert_values), batch_size):
            batch = insert_values[i:i+batch_size]
            result.append(f"INSERT INTO {table_name} ({columns}) VALUES")
            result.append(',\n'.join(batch))
            result.append(';\n')
        
        return '\n'.join(result)
    
    # Replace all COPY commands
    converted = copy_pattern.sub(replace_copy, content)
    
    # Write output
    with open(output_file, 'w', encoding='utf-8') as f:
        f.write(converted)
    
    print(f"✅ Conversion complete!")
    print(f"   Input:  {input_file}")
    print(f"   Output: {output_file}")

if __name__ == '__main__':
    if len(sys.argv) != 3:
        print("Usage: python convert_copy_to_insert.py <input_file> <output_file>")
        print("Example: python convert_copy_to_insert.py workflow_platform_v2_sqlonly.sql workflow_platform_v2_insert.sql")
        sys.exit(1)
    
    input_file = sys.argv[1]
    output_file = sys.argv[2]
    
    try:
        convert_copy_to_insert(input_file, output_file)
    except Exception as e:
        print(f"❌ Error: {e}")
        sys.exit(1)

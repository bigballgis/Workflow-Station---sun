#!/usr/bin/env python3
"""
Convert PostgreSQL COPY FROM stdin commands to INSERT statements (Version 2)
Improved handling of special characters and data validation
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
    copy_pattern = re.compile(
        r'COPY\s+([\w.]+)\s*\((.*?)\)\s+FROM\s+stdin;(.*?)\\\.',
        re.DOTALL | re.MULTILINE
    )
    
    conversion_stats = {
        'total_tables': 0,
        'total_rows': 0,
        'skipped_tables': 0,
        'errors': []
    }
    
    def replace_copy(match):
        table_name = match.group(1)
        columns_str = match.group(2)
        data_block = match.group(3).strip()
        
        conversion_stats['total_tables'] += 1
        
        # Parse column names
        columns = [col.strip() for col in columns_str.split(',')]
        num_columns = len(columns)
        
        # If no data, return empty comment
        if not data_block:
            conversion_stats['skipped_tables'] += 1
            return f"-- No data for table {table_name}\n"
        
        # Split data into lines
        lines = [line for line in data_block.split('\n') if line.strip()]
        
        if not lines:
            conversion_stats['skipped_tables'] += 1
            return f"-- No data for table {table_name}\n"
        
        # Build INSERT statements
        result = []
        result.append(f"--")
        result.append(f"-- Data for Name: {table_name}; Type: TABLE DATA; Schema: public; Owner: platform")
        result.append(f"--\n")
        
        # Process each line
        insert_values = []
        line_num = 0
        for line in lines:
            line_num += 1
            
            # Split by tab
            values = line.split('\t')
            
            # Validate column count
            if len(values) != num_columns:
                error_msg = f"Table {table_name}, line {line_num}: Expected {num_columns} columns, got {len(values)}"
                conversion_stats['errors'].append(error_msg)
                print(f"⚠️  WARNING: {error_msg}")
                print(f"    Line content: {line[:100]}...")
                # Skip this row
                continue
            
            # Convert values to SQL format
            sql_values = []
            for i, value in enumerate(values):
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
                    # Also handle backslash escapes
                    escaped_value = value.replace('\\', '\\\\').replace("'", "''")
                    sql_values.append(f"'{escaped_value}'")
            
            insert_values.append(f"({', '.join(sql_values)})")
            conversion_stats['total_rows'] += 1
        
        if not insert_values:
            conversion_stats['skipped_tables'] += 1
            return f"-- No valid data for table {table_name} (all rows had errors)\n"
        
        # Build INSERT statement
        # Split into multiple INSERT statements if too many rows (for better readability)
        batch_size = 100
        for i in range(0, len(insert_values), batch_size):
            batch = insert_values[i:i+batch_size]
            result.append(f"INSERT INTO {table_name} ({columns_str}) VALUES")
            result.append(',\n'.join(batch))
            result.append(';\n')
        
        return '\n'.join(result)
    
    # Replace all COPY commands
    converted = copy_pattern.sub(replace_copy, content)
    
    # Write output
    with open(output_file, 'w', encoding='utf-8') as f:
        f.write(converted)
    
    # Print statistics
    print(f"\n✅ Conversion complete!")
    print(f"   Input:  {input_file}")
    print(f"   Output: {output_file}")
    print(f"\n📊 Statistics:")
    print(f"   Total tables processed: {conversion_stats['total_tables']}")
    print(f"   Total rows converted:   {conversion_stats['total_rows']}")
    print(f"   Tables with no data:    {conversion_stats['skipped_tables']}")
    
    if conversion_stats['errors']:
        print(f"\n⚠️  Warnings: {len(conversion_stats['errors'])} rows skipped due to column count mismatch")
        print(f"   (See warnings above for details)")
    
    return conversion_stats

if __name__ == '__main__':
    if len(sys.argv) != 3:
        print("Usage: python convert_copy_to_insert_v2.py <input_file> <output_file>")
        print("Example: python convert_copy_to_insert_v2.py workflow_platform_v2_sqlonly.sql workflow_platform_v2_insert_v2.sql")
        sys.exit(1)
    
    input_file = sys.argv[1]
    output_file = sys.argv[2]
    
    try:
        stats = convert_copy_to_insert(input_file, output_file)
        
        if stats['errors']:
            print(f"\n⚠️  Some rows were skipped. Please review the warnings above.")
            print(f"   The output file may be missing some data.")
        
    except Exception as e:
        print(f"❌ Error: {e}")
        import traceback
        traceback.print_exc()
        sys.exit(1)

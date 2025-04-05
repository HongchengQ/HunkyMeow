import re
import random

# 输入和输出文件路径
input_file = './unknownCmdId.java'   # 替换为你的Java文件路径
output_file = './unknownCmdId.java'  # 替换为输出文件路径

# 生成6位随机数并确保不重复
def generate_unique_random_numbers(count, min_value=100000, max_value=999999):
    numbers = set()
    while len(numbers) < count:
        number = random.randint(min_value, max_value)
        numbers.add(number)
    return list(numbers)

# 读取并处理Java文件
def process_java_file(input_file, output_file):
    with open(input_file, 'r', encoding='utf-8') as file:
        content = file.readlines()

    # 匹配 public static final int 字段声明的正则表达式
    pattern = r'(public\s+static\s+final\s+int\s+\w+\s*=\s*)\d+\s*;'

    # 找出所有符合条件的行
    fields_to_replace = [line for line in content if re.search(pattern, line)]

    # 生成不重复的6位随机数，数量与需要替换的字段一致
    random_numbers = generate_unique_random_numbers(len(fields_to_replace))

    # 替换每个匹配行的值
    updated_content = []
    random_index = 0
    for line in content:
        match = re.search(pattern, line)
        if match:
            new_line = re.sub(r'=\s*\d+\s*;', f'= {random_numbers[random_index]}; //oldVersion', line)
            updated_content.append(new_line)
            random_index += 1
        else:
            updated_content.append(line)

    # 写入新的文件
    with open(output_file, 'w', encoding='utf-8') as file:
        file.writelines(updated_content)

    print(f"文件处理完成，结果已保存至：{output_file}")

if __name__ == "__main__":
    process_java_file(input_file, output_file)
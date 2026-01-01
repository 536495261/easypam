"""
分片上传测试脚本
直接在 PyCharm 中运行即可
"""
import requests
import hashlib
import os

# ============ 配置区域 ============
BASE_URL = "http://localhost:8082"
USER_ID = "2005253929332097025"
CHUNK_SIZE = 5 * 1024 * 1024  # 5MB

# 修改为你要上传的文件路径
FILE_PATH = r"D:\test_video.mp4"

# 测试模式：
# "normal" - 正常上传
# "breakpoint" - 断点续传测试（上传一半后停止，再次运行继续）
# "simulate_break" - 模拟断点（只上传前2个分片，不合并）
TEST_MODE = "normal"
# =================================


def calculate_md5(file_path):
    """计算文件MD5"""
    md5 = hashlib.md5()
    with open(file_path, 'rb') as f:
        for chunk in iter(lambda: f.read(8192), b''):
            md5.update(chunk)
    return md5.hexdigest()


def init_upload(file_name, file_size, file_md5):
    """初始化上传"""
    resp = requests.post(
        f"{BASE_URL}/file/chunk/init",
        headers={"X-User-Id": USER_ID},
        data={
            "fileName": file_name,
            "fileSize": file_size,
            "fileMd5": file_md5,
            "chunkSize": CHUNK_SIZE
        }
    )
    print(f"初始化响应: {resp.json()}")
    return resp.json()["data"]


def upload_chunk(upload_id, chunk_index, chunk_data, chunk_count):
    """上传分片"""
    resp = requests.post(
        f"{BASE_URL}/file/chunk/upload",
        headers={"X-User-Id": USER_ID},
        data={
            "uploadId": upload_id,
            "chunkIndex": chunk_index
        },
        files={"file": (f"chunk_{chunk_index}", chunk_data)}
    )
    # 显示进度
    progress = (chunk_index + 1) / chunk_count * 100
    print(f"分片 {chunk_index + 1}/{chunk_count} 上传完成 | 进度: {progress:.1f}%")
    return resp.json()


def merge_chunks(upload_id):
    """合并分片"""
    resp = requests.post(
        f"{BASE_URL}/file/chunk/merge",
        headers={"X-User-Id": USER_ID},
        data={"uploadId": upload_id}
    )
    print(f"合并响应: {resp.json()}")
    return resp.json()


def main():
    file_path = FILE_PATH
    
    if not os.path.exists(file_path):
        print(f"文件不存在: {file_path}")
        print("请修改 FILE_PATH 变量为实际文件路径")
        return

    file_name = os.path.basename(file_path)
    file_size = os.path.getsize(file_path)
    file_md5 = calculate_md5(file_path)

    print(f"文件: {file_name}")
    print(f"大小: {file_size} bytes ({file_size / 1024 / 1024:.2f} MB)")
    print(f"MD5: {file_md5}")
    print(f"测试模式: {TEST_MODE}")
    print("-" * 50)

    # 1. 初始化上传
    init_result = init_upload(file_name, file_size, file_md5)

    if init_result.get("quickUpload"):
        print("秒传成功!")
        return

    upload_id = init_result["uploadId"]
    chunk_count = init_result["chunkCount"]
    uploaded_chunks = init_result.get("uploadedChunks", [])

    print(f"上传任务ID: {upload_id}")
    print(f"总分片数: {chunk_count}")
    print(f"已上传分片: {uploaded_chunks}")
    
    # 断点续传检测
    if uploaded_chunks:
        print(f">>> 检测到断点续传！将跳过 {len(uploaded_chunks)} 个已上传分片")
    print("-" * 50)

    # 2. 上传分片
    with open(file_path, 'rb') as f:
        for i in range(chunk_count):
            # 模拟断点模式：只上传前2个分片
            if TEST_MODE == "simulate_break" and i >= 2:
                print(f">>> 模拟断点：停止上传，已上传 {i} 个分片")
                print(">>> 再次运行脚本测试断点续传")
                return
            
            if i in uploaded_chunks:
                print(f"分片 {i} 已上传，跳过（断点续传）")
                f.seek((i + 1) * CHUNK_SIZE)
                continue

            chunk_data = f.read(CHUNK_SIZE)
            upload_chunk(upload_id, i, chunk_data, chunk_count)

    print("-" * 50)

    # 3. 合并分片
    merge_chunks(upload_id)
    print("上传完成!")


if __name__ == "__main__":
    main()

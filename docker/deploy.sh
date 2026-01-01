#!/bin/bash

# 网盘微服务部署脚本

set -e

echo "========== EasyPam 部署脚本 =========="

# 检查Docker
if ! command -v docker &> /dev/null; then
    echo "Docker未安装，开始安装..."
    curl -fsSL https://get.docker.com | sh
    sudo systemctl start docker
    sudo systemctl enable docker
fi

# 检查Docker Compose
if ! command -v docker-compose &> /dev/null; then
    echo "Docker Compose未安装，开始安装..."
    sudo curl -L "https://github.com/docker/compose/releases/latest/download/docker-compose-$(uname -s)-$(uname -m)" -o /usr/local/bin/docker-compose
    sudo chmod +x /usr/local/bin/docker-compose
fi

# 获取本机IP
HOST_IP=$(hostname -I | awk '{print $1}')
echo "检测到本机IP: $HOST_IP"

# 更新broker配置
sed -i "s/brokerIP1 = .*/brokerIP1 = $HOST_IP/" broker.conf

# 创建sql目录并复制文件
mkdir -p sql
cp ../sql/*.sql sql/

# 设置ES目录权限
sudo sysctl -w vm.max_map_count=262144
echo "vm.max_map_count=262144" | sudo tee -a /etc/sysctl.conf

# 启动服务
echo "启动中间件服务..."
docker-compose -f docker-compose-linux.yml up -d

echo "等待服务启动..."
sleep 30

# 检查服务状态
echo "服务状态:"
docker-compose -f docker-compose-linux.yml ps

echo ""
echo "========== 部署完成 =========="
echo "Nacos控制台: http://$HOST_IP:8848/nacos (nacos/nacos)"
echo "MinIO控制台: http://$HOST_IP:9001 (minioadmin/minioadmin)"
echo "Sentinel控制台: http://$HOST_IP:8858"
echo "Elasticsearch: http://$HOST_IP:9200"
echo ""
echo "MySQL: $HOST_IP:3306 (root/root)"
echo "Redis: $HOST_IP:6379"
echo "RocketMQ: $HOST_IP:9876"

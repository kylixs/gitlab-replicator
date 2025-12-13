#!/bin/bash

# GitLab API 集成测试运行脚本
# 此脚本会启动 GitLab 服务器并运行集成测试

set -e

# 颜色输出
RED='\033[0;31m'
GREEN='\033[0;32m'
YELLOW='\033[1;33m'
NC='\033[0m' # No Color

echo -e "${GREEN}=== GitLab API 集成测试 ===${NC}"

# 检查环境变量
if [ -z "$SOURCE_GITLAB_TOKEN" ]; then
    echo -e "${YELLOW}警告: SOURCE_GITLAB_TOKEN 环境变量未设置${NC}"
    echo "集成测试将被跳过"
    echo ""
    echo "请按照以下步骤获取 token:"
    echo "1. 启动 GitLab: cd docker/gitlab-source && docker-compose up -d"
    echo "2. 等待启动完成（5-10分钟）"
    echo "3. 访问 http://localhost:8000"
    echo "4. 获取初始密码: docker exec -it gitlab-source cat /etc/gitlab/initial_root_password"
    echo "5. 使用 root 用户登录"
    echo "6. 创建 Personal Access Token (Settings -> Access Tokens)"
    echo "7. 设置环境变量: export SOURCE_GITLAB_TOKEN='your-token'"
    echo ""
fi

# 检查 GitLab 是否运行
GITLAB_URL="${SOURCE_GITLAB_URL:-http://localhost:8000}"
echo -e "${GREEN}检查 GitLab 服务器: ${GITLAB_URL}${NC}"

if curl -f -s "${GITLAB_URL}/-/health" > /dev/null; then
    echo -e "${GREEN}✓ GitLab 服务器运行中${NC}"
else
    echo -e "${YELLOW}GitLab 服务器未运行${NC}"
    echo "正在启动 GitLab..."

    cd docker/gitlab-source
    docker-compose up -d

    echo "等待 GitLab 启动（这可能需要 5-10 分钟）..."
    for i in {1..60}; do
        if curl -f -s "${GITLAB_URL}/-/health" > /dev/null 2>&1; then
            echo -e "${GREEN}✓ GitLab 已启动${NC}"
            break
        fi
        echo -n "."
        sleep 10
    done
    echo ""

    cd ../..
fi

# 运行集成测试
echo -e "${GREEN}运行集成测试...${NC}"
mvn test -Dtest=GitLabApiClientIntegrationTest -rf :server

echo -e "${GREEN}=== 集成测试完成 ===${NC}"

#!/usr/bin/env ruby

# 查找 root 用户
user = User.find_by(username: 'root')

if user.nil?
  puts "ERROR: Root user not found"
  exit 1
end

# 创建 Personal Access Token
token = PersonalAccessToken.create!(
  user: user,
  name: 'gitlab-mirror-source',
  scopes: [:api, :read_repository, :write_repository],
  expires_at: 365.days.from_now
)

puts "=" * 60
puts "GitLab Source Access Token Created Successfully!"
puts "=" * 60
puts ""
puts "Token Information:"
puts "  Name: #{token.name}"
puts "  Token: #{token.token}"
puts "  Expires: #{token.expires_at.to_date}"
puts "  Scopes: #{token.scopes.join(', ')}"
puts ""
puts "IMPORTANT: Copy this token now - you won't see it again!"
puts "=" * 60

# Page snapshot

```yaml
- generic [ref=e4]:
  - generic [ref=e6]:
    - heading "GitLab Mirror" [level=2] [ref=e7]
    - paragraph [ref=e8]: Sign in to your account
  - generic [ref=e10]:
    - generic [ref=e11]:
      - generic [ref=e12]: "*Username"
      - generic [ref=e15]:
        - img [ref=e18]
        - textbox "*Username" [ref=e20]:
          - /placeholder: Enter your username
          - text: admin
    - generic [ref=e21]:
      - generic [ref=e22]: "*Password"
      - generic [ref=e25]:
        - img [ref=e28]
        - textbox "*Password" [ref=e31]:
          - /placeholder: Enter your password
          - text: wrongpassword
        - img [ref=e34] [cursor=pointer]
    - alert [ref=e37]:
      - img [ref=e39]
      - generic [ref=e41]:
        - generic [ref=e42]: Warning
        - paragraph [ref=e43]:
          - generic [ref=e44]: 5 failed login attempts. Your account will be locked after 5 more failed attempts.
    - alert [ref=e45]:
      - img [ref=e47]
      - generic [ref=e49]:
        - paragraph [ref=e50]: 用户名或密码错误
        - img [ref=e52] [cursor=pointer]
    - button "Sign In" [ref=e56] [cursor=pointer]:
      - generic [ref=e58]: Sign In
```
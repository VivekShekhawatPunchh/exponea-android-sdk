## 🕵 Anonymize

Anonymize is a feature that allows you to switch users. Typical use-case is user login/logout.

Anonymize will delete all stored information and reset the curent customer. New customer will be generated, install and session start events tracked. Push notification token from the old user will be wiped and tracked for the new user, to make sure the device won't get duplicate push notifications.

#### 💻 Usage

``` kotlin
Exponea.anonymize()
```

### Project settings switch
Anonymize also allows you to switch to a different project, keeping the benefits described above. New user will have the same events as if the app was installed on a new device.

#### 💻 Usage

``` kotlin
Exponea.anonymize(
    ExponeaProject(
        baseUrl= "https://api.exponea.com",
        projectToken= "project-token",
        authorization= "Token your-auth-token"
    )
)
```


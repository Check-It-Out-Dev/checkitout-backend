from flask import Flask, request, make_response, redirect
from flask_bcrypt import Bcrypt

app = Flask(__name__)
bcrypt = Bcrypt(app)

VALID_USERNAME = "tst-admin"
VALID_PASSWORD = "tst-password"

# Serve the login page
@app.route("/login", methods=["GET"])
def login_page():
    if request.cookies.get("sessionid") == "valid_session_token":
        return make_response(redirect("/"))

    return """
      <!DOCTYPE html>
      <html lang="en">
      <head>
          <meta charset="UTF-8">
          <meta name="viewport" content="width=device-width, initial-scale=1.0">
          <title>Login Form</title>
      </head>
      <body style="margin: 0; height: 100vh; display: flex; justify-content: center; align-items: center; background-color: #121212; color: #ffffff; font-family: Arial, sans-serif;">
          <form action="/login-tst" method="POST" style="background-color: #1e1e1e; padding: 20px; border-radius: 8px; box-shadow: 0 4px 6px rgba(0, 0, 0, 0.1); width: 300px;">
              <label for="username" style="display: block; margin-bottom: 8px; font-weight: bold;">Username:</label>
              <input type="text" id="username" name="username" required style="width: 100%; padding: 8px; margin-bottom: 16px; border: 1px solid #333; border-radius: 4px; background-color: #2d2d2d; color: #ffffff;">
              <br>
              <label for="password" style="display: block; margin-bottom: 8px; font-weight: bold;">Password:</label>
              <input type="password" id="password" name="password" required style="width: 100%; padding: 8px; margin-bottom: 16px; border: 1px solid #333; border-radius: 4px; background-color: #2d2d2d; color: #ffffff;">
              <br>
              <button type="submit" style="width: 100%; padding: 10px; background-color: #6200ea; color: #ffffff; border: none; border-radius: 4px; font-size: 16px; cursor: pointer;">Login</button>
          </form>
      </body>
      </html>
    """

# Handle login form submission
@app.route("/login", methods=["POST"])
def login():
    username = request.form.get("username")
    password = request.form.get("password")

    if username == VALID_USERNAME and password == VALID_PASSWORD:
        # Set a session cookie
        response = make_response(redirect("/"))
        response.set_cookie("sessionid", "valid_session_token", httponly=True, secure=True)
        return response
    else:
        return "Invalid credentials", 401

# Validate session cookie
@app.route("/validate_session", methods=["GET"])
def validate_session():
    if request.cookies.get("sessionid") == "valid_session_token":
        return "Valid session", 200
    else:
        return "Invalid session", 401

if __name__ == "__main__":
    app.run(host="0.0.0.0", port=5000)
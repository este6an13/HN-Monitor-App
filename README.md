# HN Monitor App

This Android application uses the [Official Hacker News API](https://github.com/HackerNews/API) to get the top and best Hacker News stories with a score greater than a pre-defined threshold (`250` by default) that can be modified by the user. The application displays the retrieved stories in a list when opening the application. The user can decide to receive push notifications as well. Currently, the process runs when tha application is open. I want the process to run in as a foreground service so that the application can remain closed and still the notifications would be received. Also, it would be nice to implement a little CRUD to allow users manage keywords. That way, the process could query the new stories endpoint and notify the user if it finds an item with a title or a URL containing any of the keywords set by the user. Something that is already being explored in this repository: [Python Hacker News Monitor](https://github.com/este6an13/hacker-news-monitor).

## Usage

To try the application, open the repository in Android Studio, build the project and go to `app/build/outputs/apk/debug` folder. Get the `apk` file and install it in your phone.

## Contributing

Contributions to this project are welcome! Feel free to fork the repository, make changes, and submit pull requests.

## License

This project is licensed under the MIT License. See the [LICENSE](LICENSE) file for more details.
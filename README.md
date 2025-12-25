# HN Monitor

HN Monitor is an Android application that passively monitors Hacker News and surfaces only high-impact stories, allowing users to stay informed without repeatedly visiting the site or getting pulled into endless scrolling.

The app uses the [Official Hacker News API](https://github.com/HackerNews/API) to periodically fetch **Top** and **Best** stories, filters them by score (default: `250`), stores them locally, and displays them in a clean list UI. Optional push notifications can alert the user when new qualifying stories are found, even when the app is closed.

## Features

- Fetches **Top** and **Best** Hacker News stories
- User-configurable **score threshold**
- Background fetching using **WorkManager**
- Optional push **notifications**
- Local persistence of stories
- Stories sorted by score
- Simple, distraction-free UI with direct links to articles

## How it works

- A background worker periodically queries the Hacker News API
- New stories above the configured threshold are stored locally
- If notifications are enabled, new stories trigger a system notification
- Opening the app displays the stored stories in a sorted list

## Usage

To try the application:

1. Clone the repository
2. Open the project in **Android Studio**
3. Build the project
4. Install the generated APK from:
    ```
    app/build/outputs/apk/debug
    ```


## Roadmap / Ideas

Future improvements are mainly focused on **UX and polish**, for example:

- Logo/name update
- Better visual hierarchy for story rows
- Improved sorting and filtering options
- Smoother UI updates
- Dark mode and accessibility improvements

## License

This project is licensed under the MIT License. See the [LICENSE](LICENSE) file for details.

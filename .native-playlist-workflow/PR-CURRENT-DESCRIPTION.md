### Description

this is a separate queue patch that uses YouTube's existing playlist interface and playback engine.

> [!NOTE]
>  FYI, this patch was developed with AI assistance from OpenAI Codex Cloud (Sol 6.1 med/high) for implementation, investigation, and automated validation. i directed development and tested the patch on my phone.

this patch supports:
- play next and play last from video menus, including creating a queue in the miniplayer without interrupting browsing.
- playlist and fullscreen views, reordering, swipe removal, and clearing the queue.
- native player and notification controls, including background playback.
- shuffle queue that visibly reorders the queue, and native repeat controls.
- native save (still using an *older* ui), download and share actions for individual videos.
- saving the whole queue to an existing or new playlist (also using an older ui).
- undo for removal, clearing, and saving to an existing playlist.
- opening another regular video with an active queue inserts it after the playing entry.

### Additional context

- supports YouTube **21.20.405**, **21.16.256**, and **20.51.39**, but not **20.21.37**
- separate from Morphe’s existing queue. its **premium-style queue** setting takes priority when enabled. though when testing this patch, i'd recommend disabling it since it can override this patch's flow.
- queue state is temporary and does not persist after restarting the app.
- undo after clearing a queue is an intentional addition because i lost my queue a couple of times due to misclicks.

[test mpp and build notes](https://github.com/CaptKraken/morphe-patches/tree/builds/youtube-native-playlist-queue-dev)


### Screenshots

| Play next | Queue playlist |
| --- | --- |
| <img width="260" alt="Play next menu" src="https://github.com/user-attachments/assets/e256e3e0-0a14-4ee6-8bdd-34ff711dd318" /> | <img width="260" alt="Queue playlist" src="https://github.com/user-attachments/assets/dbaf8798-762f-4e42-a719-9f7e8f3c5df2" /> |

| Fullscreen playlist |
| --- |
|<img width="800" alt="Fullscreen playlist" src="https://github.com/user-attachments/assets/6ac0aa89-2577-48f0-8f3a-18742ef0e01a" /> |

| Save queue | Swipe to remove |
| --- | --- |
| <img width="260" alt="Save queue" src="https://github.com/user-attachments/assets/cd87d4e0-6fb1-4bb3-b449-7e9c3177713d" /> | <img width="260" alt="Swipe to remove" src="https://github.com/user-attachments/assets/7e28798d-2250-4d28-a9d5-e9c652c14ce2" /> |


### Test results

- [x] Tested on both the **minimum** and **maximum** supported versions
- [ ] Tested on **experimental** supported versions (Optional)
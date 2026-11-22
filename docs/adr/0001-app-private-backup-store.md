# Backup copies live in app-private storage

The local copies are the staging step for a future cloud upload, so they go into the app's private storage, not a user-chosen folder picked through the Storage Access Framework. Private storage gives a frozen snapshot that no other app or the user can edit before upload, needs no permission a future background uploader could lose, lets a copy be written to a temporary name, synced and renamed so it either exists in full or not at all, and is where encrypt-before-upload would naturally happen later. SAF is still used to pick the **Source files**.

## Considered Options

- **User-chosen folder via SAF (`ACTION_OPEN_DOCUMENT_TREE`)**: copies would be visible in the Files app and survive uninstall, which only matters if the local copy were the final backup. It adds revocable permissions and removable media as failure modes, and document providers don't guarantee rename or fsync, so a crash could leave a partial file under its final name.

## Consequences

- Copies are invisible outside the app and are deleted when the app is uninstalled. The app must offer a way to open a **Backup copy** so the user (and a reviewer) can see it is real.

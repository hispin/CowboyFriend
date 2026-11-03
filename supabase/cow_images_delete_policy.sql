-- Replacing the picture of a cow (the small camera of the cow card) deletes the previous picture from the storage.
-- The storage deletes only what a policy on storage.objects allows, so the logged-in users need a DELETE policy on the
-- bucket of the cow pictures. Run this once in the Supabase SQL editor.
-- (Without it the new picture is still saved, but the app tells that the old picture could not be deleted from the storage.)

drop policy if exists "cow_images_delete_authenticated" on storage.objects;

create policy "cow_images_delete_authenticated"
on storage.objects
for delete
to authenticated
using (bucket_id = 'CowsAndCalf');

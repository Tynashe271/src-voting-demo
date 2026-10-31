<?php

namespace App\Http\Controllers\Concerns;

use Illuminate\Http\Request;
use Illuminate\Support\Facades\Storage;

/**
 * Shared by the Blade (Web\ElectionController) and JSON (ElectionController) candidate endpoints
 * so there is exactly one copy of this logic to get right, not two that can drift apart.
 */
trait StoresCandidatePhoto
{
    // Students need to actually recognise who they're voting for, so a real picture upload beats
    // asking staff to paste a URL. Uses the app's default disk (FILESYSTEM_DISK) rather than a
    // hardcoded one: locally that's 'public' (this container's own ephemeral disk, fine for dev),
    // in production it's 's3' pointed at Neon Object Storage (see config/filesystems.php and the
    // s3/ candidate-photos bucket), so uploaded photos survive this container being redeployed.
    protected function storeCandidatePhoto(Request $request): ?string
    {
        if (! $request->hasFile('photo')) {
            return null;
        }

        // No subfolder prefix: locally the 'public' disk is used for nothing but candidate photos,
        // and in production the whole bucket (candidate-photos) is already dedicated to them --
        // prefixing with the same name a second time here produced a doubled path,
        // https://.../candidate-photos/candidate-photos/<file>.png, that 404'd.
        $disk = config('filesystems.default');
        $path = $request->file('photo')->store('', $disk);

        // The disk's own url() already returns an absolute URL (built from APP_URL for 'public', or
        // AWS_URL for 's3') -- prepending APP_URL again here produced a doubled, broken URL like
        // "http://host.comhttp://host.com/storage/...".
        return Storage::disk($disk)->url($path);
    }
}

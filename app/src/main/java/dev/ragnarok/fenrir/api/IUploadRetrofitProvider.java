package dev.ragnarok.fenrir.api;

import io.reactivex.rxjava3.core.Single;

public interface IUploadRetrofitProvider {
    Single<RetrofitWrapper> provideUploadRetrofit();
}